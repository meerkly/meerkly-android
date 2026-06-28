package com.meerkly.android.browser

import com.meerkly.android.logging.AppLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Collects page HTML pushed from the GeckoView WebExtension (content script ->
 * background -> native port) and hands it to a navigation that is waiting for the
 * page. Only one fetch runs at a time (workers are claimed exclusively) and
 * [reset] clears prior state at the start of each navigation, so the most recent
 * pushed page is unambiguously the one this navigation loaded — no URL matching
 * needed (the committed nav URL and the content script's location.href can differ
 * on redirects/normalization, so gating on them dropped valid HTML).
 *
 * All access is on the GeckoView UI thread: [onPage] from the port delegate and
 * [await]/[reset] from the navigate coroutine (Main dispatcher).
 */
class HtmlExtractor(private val logger: AppLogger) {

    data class Page(val url: String, val title: String?, val html: String)

    private var latest: Page? = null
    private var waiter: CompletableDeferred<Page>? = null

    /** Clear state at the start of a navigation so [await] returns only this load. */
    fun reset() {
        latest = null
        waiter = null
    }

    /** Called when the extension pushes a freshly-loaded page. */
    fun onPage(url: String, title: String?, html: String) {
        logger.info("browser.extract_page", mapOf("url" to url, "bytes" to html.length))
        val page = Page(url, title, html)
        latest = page
        waiter?.let { it.complete(page) }
        waiter = null
    }

    /**
     * Returns the page this navigation loaded, waiting up to [timeoutMs] for the
     * extension's push if it hasn't arrived yet. [finalUrl] is for logging only.
     */
    suspend fun await(finalUrl: String?, timeoutMs: Long = 5_000L): Page? {
        latest?.let { return it }
        val def = CompletableDeferred<Page>()
        waiter = def
        val res = withTimeoutOrNull(timeoutMs) { def.await() }
        if (waiter === def) waiter = null
        if (res == null) {
            logger.warn("browser.extract_timeout", mapOf("finalUrl" to finalUrl))
        }
        return res
    }
}
