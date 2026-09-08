package com.meerkly.android.auth

import android.content.Intent
import com.meerkly.android.logging.AppLogger
import com.meerkly.android.model.AuthStatus
import com.meerkly.android.model.EarningsState
import com.meerkly.android.proxy.ProxyController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Ties sign-in to the proxy.
 *
 *   - the proxy starts only once a publisher id is known — it is the SDK's
 *     required config, and there is nothing to earn for without it;
 *   - sign-in fetches that id, then starts the worker;
 *   - a signed-in install that never got an id heals itself at startup;
 *   - sign-out stops the proxy, because unlike 1.x the credential and the
 *     earning identity are now the same thing: there is no device token that
 *     keeps working after the session ends.
 *
 * Exposes the merged [AuthStatus] the UI renders (gate vs dashboard).
 */
// internal, not public: the primary constructor takes a [ProxyController],
// whose own visibility is internal (see ProxyController.kt) — Kotlin's
// visibility checker requires the enclosing declaration to be no wider than
// that. The whole app lives in one Gradle module, so this loses nothing.
internal class AccountCoordinator(
    private val auth: AuthManager,
    private val proxy: ProxyController,
    private val logger: AppLogger,
    private val scope: CoroutineScope,
    // Sticky user Stop: when false, nothing here may start the proxy. Injected
    // (with the eligibility callback) so this class stays Android-free.
    private val isWorkerEnabled: () -> Boolean = { true },
    private val onWorkerEligible: () -> Unit = {},
    // Stops the foreground service on sign-out. Injected for the same reason
    // as onWorkerEligible: this class has no Context to call
    // WorkerServiceLauncher.stop itself. Deliberately not the same callback
    // that flips worker_enabled — a signed-out user losing the service is not
    // the user pressing Stop, and conflating the two would leave earning
    // disabled after they sign back in.
    private val onSignedOut: () -> Unit = {},
) {
    private val _status = MutableStateFlow<AuthStatus>(AuthStatus.Loading)
    val status: StateFlow<AuthStatus> = _status

    // Stays Unknown until a fetch actually succeeds — a failed refresh must
    // never render as a zero balance — and a later failure keeps the last
    // loaded figure rather than clearing it.
    private val _earnings = MutableStateFlow<EarningsState>(EarningsState.Unknown)
    val earnings: StateFlow<EarningsState> = _earnings

    /** Startup: restore the session, start the proxy if we can, heal if not. */
    fun onAppStart() {
        scope.launch {
            auth.load()
            when {
                !isWorkerEnabled() ->
                    logger.info("account.proxy_deferred", mapOf("reason" to "worker stopped by user"))
                auth.publisherId != null -> proxy.start()
                auth.isSignedIn -> {
                    // Signed in, but /api/v1/me never came back — retry now.
                    logger.info("account.heal_publisher_id")
                    healPublisherId()
                }
                else -> logger.info("account.proxy_deferred", mapOf("reason" to "signed out"))
            }
            refreshStatus()
        }
    }

    /** Completes the OAuth redirect result, then starts earning. Error or null. */
    suspend fun completeSignIn(data: Intent?): String? {
        val error = auth.completeSignIn(data)
        if (error == null && auth.publisherId != null && isWorkerEnabled()) {
            proxy.start()
            // Signed in interactively with the app in the foreground — a legal
            // moment to raise the foreground service.
            onWorkerEligible()
        }
        refreshStatus()
        return error
    }

    /**
     * Sign out. Unlike 1.x this also stops the proxy and the foreground
     * service: the publisher id is both the credential's payload and the
     * earning identity, so continuing to earn — or leaving the ongoing
     * notification up — for an account the user just signed out of would be
     * wrong. Bundled here, not left to the caller, so there is no path that
     * clears the session without also stopping the service.
     */
    suspend fun signOut() {
        auth.signOut()
        proxy.shutdown()
        onSignedOut()
        _earnings.value = EarningsState.Unknown
        refreshStatus()
    }

    /** Safe any time; a failure leaves the last value in place. */
    fun refreshEarnings() {
        scope.launch {
            auth.fetchEarnings()?.let { _earnings.value = EarningsState.Loaded(it) }
        }
    }

    /**
     * Re-fetch /api/v1/me for an install that has a session but no id, and
     * start earning if one arrives. This is the actual fix for "signed in,
     * still setting up your account" — [refreshEarnings] refetches earnings,
     * which never touches publisherId at all. Public (unlike [healPublisherId]
     * before it) so the dashboard's "Try again" banner can drive the same
     * heal path [onAppStart] uses, instead of a retry that can never resolve
     * what the banner is showing.
     */
    fun retryAccount() {
        scope.launch {
            healPublisherId()
            refreshStatus()
        }
    }

    /** Re-fetch /api/v1/me for an install that has a session but no id. */
    private suspend fun healPublisherId() {
        auth.refreshAccount()
        if (auth.publisherId != null && isWorkerEnabled()) {
            proxy.start()
            onWorkerEligible()
        }
    }

    private fun refreshStatus() {
        val email = auth.email
        _status.value = if (email == null) {
            AuthStatus.SignedOut
        } else {
            refreshEarnings()
            AuthStatus.SignedIn(email, auth.publisherId)
        }
    }
}
