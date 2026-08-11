package com.meerkly.android.util

import java.time.Duration
import java.time.Instant

/**
 * "3 min ago" for the activity feed and device list.
 *
 * Pure and clock-injected — no Compose, no `System.currentTimeMillis()` — so
 * every bucket boundary is unit-testable. Returns a [Bucket] rather than a
 * string so the caller supplies localized copy from strings.xml; formatting
 * numbers here would bake English pluralisation into a util.
 */
object RelativeTime {

    sealed interface Bucket {
        /** Under a minute. */
        data object JustNow : Bucket
        data class Minutes(val value: Long) : Bucket
        data class Hours(val value: Long) : Bucket
        data object Yesterday : Bucket
        data class Days(val value: Long) : Bucket
        /** No timestamp at all — e.g. a device that has never connected. */
        data object Never : Bucket
    }

    fun bucket(then: Instant?, now: Instant): Bucket {
        if (then == null) return Bucket.Never
        val seconds = Duration.between(then, now).seconds
        // A clock skew that puts `then` slightly in the future reads better as
        // "just now" than as a negative age.
        if (seconds < 60) return Bucket.JustNow
        val minutes = seconds / 60
        if (minutes < 60) return Bucket.Minutes(minutes)
        val hours = minutes / 60
        if (hours < 24) return Bucket.Hours(hours)
        val days = hours / 24
        return if (days == 1L) Bucket.Yesterday else Bucket.Days(days)
    }
}
