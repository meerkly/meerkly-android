package com.meerkly.android.ui

import com.meerkly.android.model.NavigationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ActivityFeedTest {

    private val t0: Instant = Instant.parse("2026-08-11T12:00:00Z")

    private fun result(
        url: String = "https://example.com/page",
        finalUrl: String? = null,
        success: Boolean = true,
        loadedMs: Long? = 800,
        title: String? = "Example",
        error: String? = null,
        startedAt: Instant = t0,
    ) = NavigationResult(
        success = success,
        requestedUrl = url,
        finalUrl = finalUrl ?: url,
        title = title,
        error = error,
        startedAt = startedAt,
        finishedAt = startedAt.plusMillis(loadedMs ?: 0),
        loadedMs = loadedMs,
        htmlSizeBytes = 12_345,
    )

    @Test
    fun `host drops www and keeps the path`() {
        val row = ActivityFeed.rows(listOf(result("https://www.bbc.co.uk/news/live?x=1"))).single()
        assertEquals("bbc.co.uk", row.host)
        assertEquals("/news/live?x=1", row.path)
    }

    @Test
    fun `a bare host has no path to show`() {
        assertEquals("", ActivityFeed.rows(listOf(result("https://example.com/"))).single().path)
    }

    @Test
    fun `an unparseable URL degrades instead of throwing`() {
        // Real crawl targets include things URI refuses; the feed must not crash.
        val row = ActivityFeed.rows(listOf(result("not a url at all"))).single()
        assertEquals("not a url at all", row.host)
    }

    @Test
    fun `the redirect target is what gets shown`() {
        val row = ActivityFeed.rows(
            listOf(result(url = "https://a.test/start", finalUrl = "https://b.test/end")),
        ).single()
        assertEquals("b.test", row.host)
        // ...but the key stays pinned to the requested URL, which never changes.
        assertTrue(row.key.endsWith("|https://a.test/start"))
    }

    @Test
    fun `failures appear in the feed and are marked`() {
        val rows = ActivityFeed.rows(
            listOf(result(success = false, error = "Navigation timeout", title = null)),
        )
        assertFalse(rows.single().succeeded)
        assertEquals("Navigation timeout", rows.single().error)
    }

    @Test
    fun `keys are stable and distinguish same-url crawls by time`() {
        val a = result(startedAt = t0)
        val b = result(startedAt = t0.plusSeconds(5))
        val keys = ActivityFeed.rows(listOf(a, b)).map { it.key }
        assertEquals(2, keys.toSet().size)
        // Re-mapping the same input must produce the same key — the row is
        // selected by key, and the ring reorders as crawls land.
        assertEquals(keys, ActivityFeed.rows(listOf(a, b)).map { it.key })
    }

    @Test
    fun `summary counts pages, successes and the median duration`() {
        val s = ActivityFeed.summary(
            listOf(
                result(loadedMs = 100),
                result(loadedMs = 500, success = false),
                result(loadedMs = 900),
            ),
        )
        assertEquals(3, s.pages)
        assertEquals(2, s.succeeded)
        assertEquals(500L, s.medianMs)
        assertEquals(66, s.successPercent)
    }

    @Test
    fun `an empty feed reports no percentage rather than zero`() {
        // 0% would read as "everything failed"; there is simply nothing yet.
        val s = ActivityFeed.summary(emptyList())
        assertEquals(0, s.pages)
        assertNull(s.successPercent)
        assertNull(s.medianMs)
    }

    @Test
    fun `durations that were never recorded don't skew the median`() {
        val s = ActivityFeed.summary(listOf(result(loadedMs = null), result(loadedMs = 200)))
        assertEquals(200L, s.medianMs)
    }
}
