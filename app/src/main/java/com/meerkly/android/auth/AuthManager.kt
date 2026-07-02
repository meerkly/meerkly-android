package com.meerkly.android.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.meerkly.android.data.SecureStore
import com.meerkly.android.logging.AppLogger
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

/**
 * OAuth2 sign-in against the Meerkly account portal (Doorkeeper, Authorization
 * Code + PKCE, public client) via AppAuth — the Android counterpart of the
 * desktop's OAuthManager. Owned by the process-singleton AppGraph so token
 * refresh works with no Activity alive; AuthState is persisted encrypted via
 * [SecureStore]. Login exists only to verify device ownership: sign-out
 * revokes/clears the OAuth session but never touches the device registration.
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

    val isSignedIn: Boolean get() = email != null && authState.isAuthorized

    /** Restore the persisted session (call once at app start). */
    fun load() {
        val raw = store.get(KEY_AUTH_STATE) ?: return
        runCatching {
            authState = AuthState.jsonDeserialize(raw)
            if (authState.isAuthorized) {
                email = store.get(KEY_EMAIL)
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
            Uri.parse(REDIRECT_URI),
        ).setScopes("public", "worker").build() // PKCE (S256) + state are automatic
        return authService.getAuthorizationRequestIntent(request)
    }

    /**
     * Completes sign-in from the redirect result: code exchange, then a
     * /api/me lookup for the account email. Returns an error message or null.
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

        val fetchedEmail = fetchEmail(token.accessToken ?: "")
        if (fetchedEmail == null) {
            // Mirror the desktop: no userinfo -> the sign-in didn't complete.
            authState = AuthState()
            return "Sign-in failed. Please try again."
        }

        email = fetchedEmail
        persist()
        logger.info("auth.signed_in", mapOf("email" to fetchedEmail))
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
     * session. The device registration/token and the running worker are
     * intentionally untouched — login only verifies ownership.
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
        store.remove(KEY_AUTH_STATE)
        store.remove(KEY_EMAIL)
    }

    private fun persist() {
        store.put(KEY_AUTH_STATE, authState.jsonSerializeString())
        email?.let { store.put(KEY_EMAIL, it) }
    }

    private suspend fun fetchEmail(accessToken: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(
                Request.Builder()
                    .url("$baseUrl/api/me")
                    .header("Authorization", "Bearer $accessToken")
                    .build(),
            ).execute().use { res ->
                if (!res.isSuccessful) return@use null
                JSONObject(res.body?.string() ?: "").optString("email").takeIf { it.isNotBlank() }
            }
        }.onFailure { logger.warn("auth.userinfo_failed", mapOf("error" to it.message)) }.getOrNull()
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

        // Must byte-match the redirect_uri seeded for the meerkly-android client
        // AND the appAuthRedirectScheme manifest placeholder.
        const val REDIRECT_URI = "com.meerkly.android:/oauth2redirect"

        private const val KEY_AUTH_STATE = "auth_state"
        private const val KEY_EMAIL = "account_email"
    }
}
