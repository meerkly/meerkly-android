package com.meerkly.android.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.meerkly.android.BuildConfig
import com.meerkly.android.data.SecureStore
import com.meerkly.android.logging.AppLogger
import com.meerkly.android.model.DeviceEarnings
import com.meerkly.android.model.Earnings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.connectivity.ConnectionBuilder
import net.openid.appauth.connectivity.DefaultConnectionBuilder
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** Thrown when /api/v1/me refuses because the account's email is unverified. */
class EmailUnverifiedException : Exception()

/**
 * OAuth2 sign-in against the Meerkly account portal (Doorkeeper, Authorization
 * Code + PKCE, public client) via AppAuth — the Android counterpart of the
 * desktop's OAuthManager. Owned by the process-singleton AppGraph so token
 * refresh works with no Activity alive; AuthState is persisted encrypted via
 * [SecureStore]. Sign-out revokes/clears the OAuth session; the caller
 * ([com.meerkly.android.AccountCoordinator]) also stops the proxy, because in
 * 2.0 the publisher id fetched here is both the proxy's credential payload
 * and the earning identity — there is no separate device registration left
 * running once the account is signed out.
 */
class AuthManager(
    appContext: Context,
    accountBaseUrl: String,
    private val logger: AppLogger,
    private val store: SecureStore,
    private val allowInsecureHttp: Boolean = false,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build(),
) {
    private val baseUrl = accountBaseUrl.trimEnd('/')

    // Doorkeeper exposes no .well-known discovery document; endpoints are static.
    private val serviceConfig = AuthorizationServiceConfiguration(
        Uri.parse("$baseUrl/oauth/authorize"),
        Uri.parse("$baseUrl/oauth/token"),
    )

    // AppAuth's DefaultConnectionBuilder refuses non-HTTPS endpoints; dev builds
    // talk to the Rails server over cleartext, so they get a permissive builder.
    private val authService = AuthorizationService(
        appContext,
        AppAuthConfiguration.Builder()
            .setConnectionBuilder(if (allowInsecureHttp) InsecureConnectionBuilder else DefaultConnectionBuilder.INSTANCE)
            .build(),
    )

    @Volatile
    private var authState: AuthState = AuthState()

    @Volatile
    var email: String? = null
        private set

    /**
     * The account's personal publisher id, fetched at sign-in and persisted.
     * This is what the proxy SDK is given; without it the app knows who the
     * user is but not where their money goes.
     */
    @Volatile
    var publisherId: String? = null
        private set

    val isSignedIn: Boolean get() = email != null && authState.isAuthorized

    /** Restore the persisted session (call once at app start). */
    fun load() {
        val raw = store.get(KEY_AUTH_STATE) ?: return
        runCatching {
            authState = AuthState.jsonDeserialize(raw)
            if (authState.isAuthorized) {
                // A session minted against a different account host (e.g. a
                // 1.x install's account.meerkly.com session surviving an
                // update to a build whose ACCOUNT_BASE_URL is
                // dashboard.meerkly.com) cannot be refreshed or used: AppAuth
                // serializes the token endpoint into the AuthState itself, so
                // refresh keeps targeting the old host regardless of what
                // this build is configured with. isSignedIn would report
                // true forever with no working publisherId and no way to
                // heal, so discard it here and drop back to the sign-in
                // gate — a working sign-in screen is strictly better than a
                // signed-in screen that can never finish.
                val sessionTokenEndpoint =
                    authState.authorizationServiceConfiguration?.tokenEndpoint?.toString()
                if (!sessionHostMatches(baseUrl, sessionTokenEndpoint)) {
                    logger.warn(
                        "auth.session_discarded_host_changed",
                        mapOf("configuredHost" to baseUrl, "sessionTokenEndpoint" to sessionTokenEndpoint),
                    )
                    clearSession()
                    return@runCatching
                }
                email = store.get(KEY_EMAIL)
                publisherId = store.get(KEY_PUBLISHER_ID)
            }
        }.onFailure {
            logger.warn("auth.restore_failed", mapOf("error" to it.message))
        }
    }

    /** The browser intent that starts the sign-in flow (launch for result). */
    fun signInIntent(): Intent {
        val request = AuthorizationRequest.Builder(
            serviceConfig,
            CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse(BuildConfig.OAUTH_REDIRECT_URI),
        ).setScopes("public").build() // PKCE (S256) + state are automatic
        return authService.getAuthorizationRequestIntent(request)
    }

    /**
     * Completes sign-in from the redirect result: code exchange, then a
     * /api/v1/me lookup for the account email and publisher id. Returns an
     * error message or null.
     */
    suspend fun completeSignIn(data: Intent?): String? {
        if (data == null) return "Sign-in was cancelled."
        val response = AuthorizationResponse.fromIntent(data)
        val authEx = AuthorizationException.fromIntent(data)
        if (response == null) {
            logger.warn("auth.authorize_failed", mapOf("error" to authEx?.errorDescription))
            return if (authEx?.code == AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW.code) {
                "Sign-in was cancelled."
            } else {
                authEx?.errorDescription ?: "Sign-in failed."
            }
        }

        val (token, tokenEx) = suspendCancellableCoroutine { cont ->
            authService.performTokenRequest(response.createTokenExchangeRequest()) { t, e ->
                cont.resume(t to e)
            }
        }
        authState = AuthState().apply {
            update(response, authEx)
            update(token, tokenEx)
        }
        if (token == null || !authState.isAuthorized) {
            logger.warn("auth.exchange_failed", mapOf("error" to tokenEx?.errorDescription))
            return "Sign-in failed. Please try again."
        }

        val account = try {
            fetchAccount(token.accessToken ?: "")
        } catch (e: EmailUnverifiedException) {
            authState = AuthState()
            return MESSAGE_EMAIL_UNVERIFIED
        }
        if (account == null) {
            // No userinfo means the sign-in did not complete.
            authState = AuthState()
            return "Sign-in failed. Please try again."
        }

        email = account.first
        publisherId = account.second
        persist()
        logger.info("auth.signed_in", mapOf("email" to account.first, "paired" to (account.second != null)))
        return null
    }

    /**
     * A fresh access token (auto-refreshed by AppAuth with its built-in 60s
     * expiry tolerance), or null when signed out / refresh is dead.
     */
    suspend fun getAccessToken(): String? {
        if (!authState.isAuthorized) return null
        val (access, ex) = suspendCancellableCoroutine { cont ->
            authState.performActionWithFreshTokens(authService) { accessToken, _, e ->
                cont.resume(accessToken to e)
            }
        }
        if (ex != null) {
            logger.warn("auth.refresh_failed", mapOf("error" to ex.errorDescription, "code" to ex.code))
            // invalid_grant = the refresh token was revoked server-side: sign out.
            if (ex.type == AuthorizationException.TYPE_OAUTH_TOKEN_ERROR) {
                clearSession()
            }
            return null
        }
        persist() // a refresh may have rotated tokens
        return access
    }

    /**
     * Sign out: best-effort server-side revocation, then clear the OAuth
     * session. The publisher id is the proxy's credential in 2.0, so the
     * caller stops the running worker itself once this returns — nothing
     * here is left registered for the account to keep earning against.
     */
    suspend fun signOut() {
        val refresh = authState.refreshToken
        if (refresh != null && baseUrl.isNotBlank()) {
            withContext(Dispatchers.IO) {
                runCatching {
                    http.newCall(
                        Request.Builder()
                            .url("$baseUrl/oauth/revoke")
                            .post(
                                FormBody.Builder()
                                    .add("token", refresh)
                                    .add("client_id", CLIENT_ID)
                                    .build(),
                            )
                            .build(),
                    ).execute().close()
                }.onFailure { logger.warn("auth.revoke_failed", mapOf("error" to it.message)) }
            }
        }
        clearSession()
        logger.info("auth.signed_out")
    }

    private fun clearSession() {
        authState = AuthState()
        email = null
        publisherId = null
        store.remove(KEY_AUTH_STATE)
        store.remove(KEY_EMAIL)
        store.remove(KEY_PUBLISHER_ID)
    }

    private fun persist() {
        store.put(KEY_AUTH_STATE, authState.jsonSerializeString())
        email?.let { store.put(KEY_EMAIL, it) }
        publisherId?.let { store.put(KEY_PUBLISHER_ID, it) }
    }

    /** (email, publisherId) from /api/v1/me, or null if the call failed. */
    private suspend fun fetchAccount(accessToken: String): Pair<String, String?>? =
        withContext(Dispatchers.IO) {
            runCatching {
                http.newCall(
                    Request.Builder()
                        .url("$baseUrl/api/v1/me")
                        .header("Authorization", "Bearer $accessToken")
                        .build(),
                ).execute().use { res ->
                    val bodyString = res.body?.string()
                    if (isEmailUnverified(res.code, bodyString)) throw EmailUnverifiedException()
                    if (!res.isSuccessful) return@use null
                    val json = JSONObject(bodyString ?: "")
                    val mail = parseEmail(json) ?: return@use null
                    mail to parsePublisherId(json)
                }
            }.onFailure {
                // EmailUnverifiedException is a distinct, user-actionable
                // result, not a fetch failure — let it propagate to
                // completeSignIn rather than being folded into the null return.
                if (it is EmailUnverifiedException) throw it
                logger.warn("auth.userinfo_failed", mapOf("error" to it.message))
            }.getOrNull()
        }

    /**
     * Re-read /api/v1/me on an existing session. Used to heal an install that
     * signed in but never received a publisher id — a network failure at that
     * moment would otherwise leave it permanently unable to earn.
     */
    suspend fun refreshAccount() {
        val accessToken = getAccessToken() ?: return
        val account = runCatching { fetchAccount(accessToken) }.getOrNull() ?: return
        email = account.first
        publisherId = account.second
        persist()
    }

    /**
     * Earnings from /api/v1/earnings. Null on any failure, so the UI keeps its
     * last value rather than showing a wrong one — never a zero.
     */
    suspend fun fetchEarnings(): Earnings? = withContext(Dispatchers.IO) {
        val accessToken = getAccessToken() ?: return@withContext null
        runCatching {
            http.newCall(
                Request.Builder()
                    .url("$baseUrl/api/v1/earnings")
                    .header("Authorization", "Bearer $accessToken")
                    .build(),
            ).execute().use { res ->
                if (!res.isSuccessful) return@use null
                parseEarnings(JSONObject(res.body?.string() ?: ""))
            }
        }.onFailure { logger.warn("auth.earnings_failed", mapOf("error" to it.message)) }.getOrNull()
    }

    /** Dev-only: permits the cleartext Rails endpoints of debug builds. */
    private object InsecureConnectionBuilder : ConnectionBuilder {
        override fun openConnection(uri: Uri): HttpURLConnection {
            val conn = URL(uri.toString()).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 10_000
            conn.instanceFollowRedirects = false
            return conn
        }
    }

    companion object {
        const val CLIENT_ID = "meerkly-android"

        // The redirect URI is BuildConfig.OAUTH_REDIRECT_URI (set per build
        // type in app/build.gradle.kts), not a constant here, because it must
        // byte-match two things that are themselves build-type specific: the
        // server's seeded redirect_uri (derived from that server's own
        // APP_HOST) and the intent filter for that build type in
        // AndroidManifest.xml — release's verified https App Link, debug's
        // plain filter for the dev server. It is an App Link rather than a
        // private-use scheme because any app on the device can claim a
        // scheme, and this client skips the consent screen — see RFC 8252
        // §8.1.

        /** The API's one refusal a user can actually act on. */
        const val ERROR_EMAIL_UNVERIFIED = "email_verification_required"

        /**
         * The user-facing message for [ERROR_EMAIL_UNVERIFIED] — actionable,
         * as opposed to the generic "Sign-in failed" text used everywhere
         * else completeSignIn gives up. Pulled into a constant so tests can
         * assert against the real value rather than a copy of the literal.
         */
        const val MESSAGE_EMAIL_UNVERIFIED = "Confirm your email address to finish setting up Meerkly."

        private const val KEY_AUTH_STATE = "auth_state"
        private const val KEY_EMAIL = "account_email"
        private const val KEY_PUBLISHER_ID = "publisher_id"

        // Parsing is in the companion so it can be tested without an Android
        // Context, an AuthorizationService or a live socket.

        /**
         * Does this response mean "the account's email is unproven", rather than
         * an ordinary failure?
         *
         * Pulled out as a pure function because it is the one branch in the
         * fetch path with a user-visible consequence — the difference between
         * telling someone to confirm their address and silently failing a
         * sign-in they will keep retrying.
         */
        fun isEmailUnverified(code: Int, body: String?): Boolean =
            code == 403 &&
                runCatching { JSONObject(body ?: "").optString("error") }.getOrNull() == ERROR_EMAIL_UNVERIFIED

        /**
         * Is a restored session's token endpoint still usable against the
         * account host this build is configured for?
         *
         * AppAuth serializes the authorization-service configuration (and so
         * the token endpoint) into the persisted AuthState itself, so a
         * session minted against one host cannot be refreshed against
         * another — the host has to match, not just be reachable. A null
         * endpoint is not evidence of a host change (e.g. a legacy or
         * partially-formed AuthState), so it is treated as usable rather than
         * discarded. Pulled out as a pure function, comparing plain strings
         * via java.net.URI, so this can be tested without an AppAuth
         * AuthorizationService or an android.net.Uri needing Robolectric.
         */
        fun sessionHostMatches(configuredBaseUrl: String, sessionTokenEndpoint: String?): Boolean {
            if (sessionTokenEndpoint == null) return true
            return runCatching {
                val configured = java.net.URI(configuredBaseUrl)
                val session = java.net.URI(sessionTokenEndpoint)
                configured.host == session.host && configured.port == session.port
            }.getOrDefault(false)
        }

        fun parseEmail(json: JSONObject): String? =
            json.optString("email").takeIf { it.isNotBlank() }

        /**
         * Blank and absent both become null. optString returns "" for a missing
         * key, and an empty publisher id would be handed to the SDK as if it
         * were real.
         */
        fun parsePublisherId(json: JSONObject): String? =
            json.optString("publisher_id").takeIf { it.isNotBlank() }

        fun parseEarnings(json: JSONObject): Earnings {
            val devices = json.optJSONArray("devices")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val d = arr.getJSONObject(i)
                    DeviceEarnings(
                        deviceId = d.optString("device_id"),
                        label = d.optString("label"),
                        online = d.optBoolean("online", false),
                        bytes30d = d.optLong("bytes_30d"),
                        usd30d = d.optDouble("usd_30d", 0.0),
                        pending = d.optBoolean("pending", false),
                    )
                }
            }.orEmpty()
            return Earnings(
                unpaidUsd = json.optDouble("unpaid_usd", 0.0),
                lifetimeUsd = json.optDouble("lifetime_usd", 0.0),
                pendingUsd = json.optDouble("pending_usd", 0.0),
                bytesShared = json.optLong("bytes_shared"),
                settledBytes = json.optLong("settled_bytes"),
                usdPerGb = json.optDouble("usd_per_gb", 0.0),
                minimumPayoutUsd = json.optDouble("minimum_payout_usd", 0.0),
                canRequestPayout = json.optBoolean("can_request_payout", false),
                devicesWindowDays = json.optInt("devices_window_days", 30),
                devices = devices,
            )
        }
    }
}
