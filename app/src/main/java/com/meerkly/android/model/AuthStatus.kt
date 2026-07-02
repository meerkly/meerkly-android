package com.meerkly.android.model

/**
 * Sign-in + device-link state driving the root UI (gate vs dashboard) — the
 * Android counterpart of the desktop's merged AuthStatus. `deviceLinked`
 * reports whether THIS install is registered to the signed-in user's account
 * (the worker only connects to the gateway once linked).
 */
sealed interface AuthStatus {
    /** Persisted state still being read on launch. */
    data object Loading : AuthStatus

    data object SignedOut : AuthStatus

    data class SignedIn(
        val email: String,
        val deviceLinked: Boolean,
        val deviceLinkError: String? = null,
    ) : AuthStatus
}
