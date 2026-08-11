package com.meerkly.android.ui

import com.meerkly.android.model.NavigationResult
import java.net.URI
import java.time.Instant

/**
 * Turns raw [NavigationResult]s into rows the activity feed can render.
 *
 * Pure so every case is unit-testable: hostname extraction has to survive URLs
 * that `URI` refuses to parse, and the row key has to stay stable while the
 * underlying ring shifts as new crawls land.
 */
object ActivityFeed {

    data class Row(
        /** Stable across ring shifts — NavigationResult has no id of its own. */
        val key: String,
        val host: String,
        val path: String,
        val title: String?,
        val succeeded: Boolean,
        val error: String?,
        val loadedMs: Long?,
        val sizeBytes: Long?,
        val startedAt: Instant,
        val finalUrl: String?,
        val requestedUrl: String,
    )

    data class Summary(
        val pages: Int,
        val succeeded: Int,
        val medianMs: Long?,
    ) {
        /** 0–100, or null when there's nothing to average yet. */
        val successPercent: Int? get() = if (pages == 0) null else succeeded * 100 / pages
    }

    fun rows(results: List<NavigationResult>): List<Row> = results.map { r ->
        val shown = r.finalUrl ?: r.requestedUrl
        Row(
            key = key(r),
            host = host(shown),
            path = path(shown),
            title = r.title?.takeIf { it.isNotBlank() },
            succeeded = r.success,
            error = r.error,
            loadedMs = r.loadedMs,
            sizeBytes = r.htmlSizeBytes,
            startedAt = r.startedAt,
            finalUrl = r.finalUrl,
            requestedUrl = r.requestedUrl,
        )
    }

    /** Failures are included and marked — a feed that hides them is dishonest. */
    fun summary(results: List<NavigationResult>): Summary {
        val durations = results.mapNotNull { it.loadedMs }.sorted()
        return Summary(
            pages = results.size,
            succeeded = results.count { it.success },
            medianMs = durations.getOrNull(durations.size / 2),
        )
    }

    fun key(result: NavigationResult): String =
        "${result.startedAt.toEpochMilli()}|${result.requestedUrl}"

    /** Host without "www.", falling back to the raw string when it won't parse. */
    internal fun host(url: String): String = runCatching {
        URI(url).host?.removePrefix("www.")
    }.getOrNull() ?: url.substringAfter("://").substringBefore("/").ifBlank { url }

    internal fun path(url: String): String = runCatching {
        val u = URI(url)
        val q = u.rawQuery?.let { "?$it" }.orEmpty()
        (u.rawPath.orEmpty() + q).takeIf { it.isNotBlank() && it != "/" }
    }.getOrNull().orEmpty()
}
