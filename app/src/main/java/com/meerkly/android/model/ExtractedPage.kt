package com.meerkly.android.model

/**
 * Result of local HTML extraction. Stubbed for the vertical slice; populated in Phase 3 via the
 * GeckoView WebExtension + native-messaging bridge.
 */
data class ExtractedPage(
    val finalUrl: String,
    val title: String?,
    val html: String?,
    val htmlSizeBytes: Long,
)
