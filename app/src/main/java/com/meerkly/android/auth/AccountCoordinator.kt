package com.meerkly.android.auth

import android.content.Intent
import com.meerkly.android.data.DeviceRegistrationManager
import com.meerkly.android.gateway.GatewayClient
import com.meerkly.android.logging.AppLogger
import com.meerkly.android.model.AuthStatus
import com.meerkly.android.model.CreditsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Ties sign-in, device registration, and the gateway worker together — the
 * Android counterpart of the desktop's index.ts wiring:
 *   - the gateway connects ONLY once this device is paired (it would be
 *     rejected otherwise);
 *   - after sign-in the device is auto-registered, then the worker (re)connects
 *     with the fresh token;
 *   - a signed-in-but-unregistered install heals itself at startup;
 *   - sign-out never unregisters the device or stops the worker.
 * Exposes the merged [AuthStatus] the UI renders (gate vs dashboard).
 */
class AccountCoordinator(
    private val auth: AuthManager,
    private val registration: DeviceRegistrationManager,
    private val gateway: GatewayClient,
    private val logger: AppLogger,
    private val scope: CoroutineScope,
    // Sticky user Stop: when false, nothing here may start/reconnect the
    // worker. Injected (with the eligibility callback) so this class stays
    // Android-free.
    private val isWorkerEnabled: () -> Boolean = { true },
    private val onWorkerEligible: () -> Unit = {},
) {
    private val _status = MutableStateFlow<AuthStatus>(AuthStatus.Loading)
    val status: StateFlow<AuthStatus> = _status

    // The signed-in user's earnings, refreshed on sign-in, startup, and whenever
    // the dashboard asks. Stays Unknown until a fetch actually succeeds — a
    // failed refresh must never be rendered as a zero balance — and a later
    // failure keeps the last loaded figure rather than clearing it.
    private val _credits = MutableStateFlow<CreditsState>(CreditsState.Unknown)
    val credits: StateFlow<CreditsState> = _credits

    /** Startup: restore session, gate the worker on pairing, heal if needed. */
    fun onAppStart() {
        scope.launch {
            auth.load()
            when {
                !isWorkerEnabled() ->
                    logger.info("account.gateway_deferred", mapOf("reason" to "worker stopped by user"))
                registration.getDeviceToken() != null -> gateway.start()
                auth.isSignedIn -> {
                    // Signed in before pairing existed (or a prior attempt
                    // failed): heal by registering now.
                    logger.info("account.heal_registration")
                    registerAndConnect()
                }
                else -> logger.info("account.gateway_deferred", mapOf("reason" to "device not paired"))
            }
            refreshStatus()
        }
    }

    /** Completes the OAuth redirect result, then pairs + connects. Returns an error or null. */
    suspend fun completeSignIn(data: Intent?): String? {
        val error = auth.completeSignIn(data)
        if (error == null) {
            registerAndConnect()
        }
        refreshStatus()
        return error
    }

    /** Sign out of the account; the device registration and worker keep going. */
    suspend fun signOut() {
        auth.signOut()
        _credits.value = CreditsState.Unknown
        refreshStatus()
    }

    /**
     * Refresh earnings from the account service. Safe to call any time; a failure
     * (or being signed out) leaves the last value in place.
     */
    fun refreshCredits() {
        scope.launch {
            auth.fetchCredits()?.let { _credits.value = CreditsState.Loaded(it) }
        }
    }

    private suspend fun registerAndConnect() {
        val accessToken = auth.getAccessToken() ?: return
        val link = registration.register(accessToken)
        if (link.deviceLinked && isWorkerEnabled()) {
            // Fresh token must be presented immediately (also clears a paused
            // client after a terminal auth failure).
            gateway.reconnect()
            // Paired during an interactive sign-in — raise the foreground
            // service so the worker survives backgrounding from here on.
            onWorkerEligible()
        }
    }

    private fun refreshStatus() {
        val email = auth.email
        _status.value = if (email == null) {
            AuthStatus.SignedOut
        } else {
            val link = registration.linkStatus()
            // Pull fresh earnings whenever we become/refresh signed-in.
            refreshCredits()
            AuthStatus.SignedIn(email, link.deviceLinked, link.deviceLinkError)
        }
    }
}
