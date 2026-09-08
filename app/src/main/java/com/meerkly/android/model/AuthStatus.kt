package com.meerkly.android.model

/**
 * Sign-in state driving the root UI (gate vs dashboard) — the Android
 * counterpart of the desktop's merged AuthStatus.
 */
sealed interface AuthStatus {
    /** Persisted state still being read on launch. */
    data object Loading : AuthStatus

    data object SignedOut : AuthStatus

    /**
     * [publisherId] is null when sign-in succeeded but /api/v1/me could not be
     * read — the user is known, but the account we would earn for is not, and
     * the proxy cannot start. The UI must offer a retry rather than a dead
     * "connecting" state.
     */
    data class SignedIn(
        val email: String,
        val publisherId: String?,
    ) : AuthStatus
}
