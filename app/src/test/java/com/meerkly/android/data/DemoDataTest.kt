package com.meerkly.android.data

import com.meerkly.android.ui.ActivityFeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * DemoData feeds the debug-only `meerkly.demo` launch extra used by
 * scripts/screenshots.sh: a cold-started app has an empty in-memory activity
 * ring, so store screenshots of the Activity tab would always show the empty
 * state without seeded placeholder crawls.
 */
class DemoDataTest {

    private val now = Instant.parse("2026-08-12T09:00:00Z")

    @Test
    fun `seeds a feed's worth of recent crawls, newest first`() {
        val repo = RecentNavigationRepository()
        DemoData.seed(repo, now)

        val results = repo.recent.value
        assertTrue("expected a handful of rows, got ${results.size}", results.size >= 5)
        val times = results.map { it.startedAt }
        assertEquals("newest first", times.sortedDescending(), times)
        assertTrue("all in the past", times.all { !it.isAfter(now) })
    }

    @Test
    fun `includes both successes and a failure so the feed looks honest`() {
        val repo = RecentNavigationRepository()
        DemoData.seed(repo, now)

        val results = repo.recent.value
        val successes = results.filter { it.success }
        val failures = results.filter { !it.success }
        assertTrue("needs successes", successes.isNotEmpty())
        assertTrue("needs at least one failure", failures.isNotEmpty())
        assertTrue(
            "successes carry title, final url, timing and size",
            successes.all {
                it.title != null && it.finalUrl != null && it.loadedMs != null && it.htmlSizeBytes != null
            },
        )
        assertTrue("failures carry an error", failures.all { it.error != null })
    }

    @Test
    fun `seeded rows render through ActivityFeed`() {
        val repo = RecentNavigationRepository()
        DemoData.seed(repo, now)

        val rows = ActivityFeed.rows(repo.recent.value)
        assertTrue("every row has a host", rows.all { it.host.isNotBlank() })

        val summary = ActivityFeed.summary(repo.recent.value)
        assertEquals(repo.recent.value.size, summary.pages)
        assertNotNull("median needs durations", summary.medianMs)
        val percent = summary.successPercent
        assertNotNull(percent)
        assertTrue("mixed outcomes land strictly between 0 and 100", percent!! in 1..99)
    }
}
