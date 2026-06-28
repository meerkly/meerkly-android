package com.meerkly.android.model

import java.time.Instant

/**
 * Outcome of a single navigation initiated from the local URL input field.
 * No jobId is needed for the local MVP.
 */
data class NavigationResult(
    val success: Boolean,
    val requestedUrl: String,
    val finalUrl: String?,
    val title: String?,
    val error: String?,
    val startedAt: Instant,
    val finishedAt: Instant,
    val loadedMs: Long?,
    val htmlSizeBytes: Long?,
) {
    /** Snake-cased map for JSONL logs and diagnostics export. */
    fun toJsonMap(): Map<String, Any?> = linkedMapOf(
        "success" to success,
        "requested_url" to requestedUrl,
        "final_url" to finalUrl,
        "title" to title,
        "error" to error,
        "started_at" to startedAt.toString(),
        "finished_at" to finishedAt.toString(),
        "loaded_ms" to loadedMs,
        "html_size_bytes" to htmlSizeBytes,
    )
}
