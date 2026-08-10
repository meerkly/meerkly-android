package com.meerkly.android.browser

import com.meerkly.android.model.NavigationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Pages that hold a connection open (ads, long-polling, streaming) never fire
 * GeckoView's onPageStop, so navigation times out even though the document
 * rendered and the extension already pushed a snapshot. Discarding that snapshot
 * made such pages fail outright — the gateway answered 502 with no HTML, where
 * desktop/headless return the document with wait_timed_out=true.
 */
class NavigationSalvageTest {

    private val now: Instant = Instant.parse("2026-08-10T12:00:00Z")

    private fun timedOutNav() = NavigationResult(
        success = false,
        requestedUrl = "https://example.com",
        finalUrl = "https://example.com",
        title = "Example",
        error = "Navigation timeout after 30000 ms",
        startedAt = now,
        finishedAt = now,
        loadedMs = 30_000,
        htmlSizeBytes = null,
    )

    private fun page(html: String) = HtmlExtractor.Page(
        url = "https://example.com",
        title = "Example",
        html = html,
        final = false,
        matchedRule = 2,
        httpStatus = 200,
        format = "html",
    )

    @Test
    fun `a captured snapshot survives a navigation timeout`() {
        val out = GeckoBrowserManager.salvage(timedOutNav(), page("<html>hi</html>"))

        assertEquals("<html>hi</html>", out.html)
        assertTrue("must report success so the gateway returns 200, not 502", out.nav.success)
        assertTrue("the caller has to know the wait never completed", out.waitTimedOut)
        assertNull("a salvaged page is not an error", out.nav.error)
    }

    @Test
    fun `salvage carries the page's own metadata, not defaults`() {
        val out = GeckoBrowserManager.salvage(timedOutNav(), page("<html>hi</html>"))

        // httpStatus drives credits — defaulting it to 0 would silently unpay a
        // worker for a page it really did fetch.
        assertEquals(200, out.httpStatus)
        assertEquals(2, out.matchedRule)
        assertEquals("html", out.format)
        assertEquals(15L, out.nav.htmlSizeBytes)
    }

    @Test
    fun `json payloads keep their format through salvage`() {
        val out = GeckoBrowserManager.salvage(
            timedOutNav(),
            page("""{"a":1}""").copy(format = "json"),
        )
        assertEquals("json", out.format)
    }

    @Test
    fun `nothing captured stays a genuine failure`() {
        val out = GeckoBrowserManager.salvage(timedOutNav(), null)

        assertNull(out.html)
        assertFalse("no document means the job really did fail", out.nav.success)
        assertFalse(out.waitTimedOut)
        assertEquals("Navigation timeout after 30000 ms", out.nav.error)
    }
}
