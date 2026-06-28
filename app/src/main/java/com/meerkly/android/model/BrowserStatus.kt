package com.meerkly.android.model

/** Current state of the visible browser, surfaced to the UI as a StateFlow. */
sealed interface BrowserStatus {
    data object Idle : BrowserStatus
    data class Loading(val requestedUrl: String) : BrowserStatus
    data class Success(val result: NavigationResult) : BrowserStatus
    data class Error(val result: NavigationResult) : BrowserStatus
}
