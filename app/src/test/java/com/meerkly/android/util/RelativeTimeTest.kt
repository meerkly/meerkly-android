package com.meerkly.android.util

import com.meerkly.android.util.RelativeTime.Bucket
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class RelativeTimeTest {

    private val now: Instant = Instant.parse("2026-08-11T12:00:00Z")
    private fun ago(seconds: Long) = RelativeTime.bucket(now.minusSeconds(seconds), now)

    @Test
    fun `buckets by age`() {
        assertEquals(Bucket.JustNow, ago(0))
        assertEquals(Bucket.JustNow, ago(59))
        assertEquals(Bucket.Minutes(1), ago(60))
        assertEquals(Bucket.Minutes(59), ago(59 * 60))
        assertEquals(Bucket.Hours(1), ago(60 * 60))
        assertEquals(Bucket.Hours(23), ago(23 * 3600))
        assertEquals(Bucket.Yesterday, ago(24 * 3600))
        assertEquals(Bucket.Days(3), ago(3 * 24 * 3600))
    }

    @Test
    fun `no timestamp is Never, not zero-seconds-ago`() {
        // A device that has never connected must not read as "just now".
        assertEquals(Bucket.Never, RelativeTime.bucket(null, now))
    }

    @Test
    fun `a future timestamp reads as just now rather than negative`() {
        // Server clock skew shouldn't surface as "-2 minutes ago".
        assertEquals(Bucket.JustNow, RelativeTime.bucket(now.plusSeconds(120), now))
    }
}
