package com.meerkly.android.model

/**
 * Whether we actually know the user's balance.
 *
 * This exists because "not loaded" and "zero" are the same value in a `Long`,
 * and rendering the former as the latter tells someone their earnings are gone
 * when the account service is merely unreachable. Credits live on the server;
 * the app never has the authority to claim a balance is zero — only to report
 * the last figure the server gave it, or admit it doesn't know.
 */
sealed interface CreditsState {

    /** Never successfully loaded on this launch — show a placeholder, never 0. */
    data object Unknown : CreditsState

    /** A real figure from the account service. */
    data class Loaded(val credits: Credits) : CreditsState

    /** The balance if known, else null. Callers must handle null explicitly
     *  rather than defaulting it to zero. */
    val creditsOrNull: Credits?
        get() = (this as? Loaded)?.credits
}
