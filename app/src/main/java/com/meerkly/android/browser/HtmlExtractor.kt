package com.meerkly.android.browser

import com.meerkly.android.logging.AppLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Collects page HTML pushed from the GeckoView WebExtension. The content script
 * sends an early "fallback" snapshot (final=false) at DOMContentLoaded and a
 * "final" snapshot (final=true) once the wait_for condition holds. [awaitFinal]
 * resolves on the final snapshot; callers fall back to [latestPage] on timeout,
 * so a never-met condition still yields best-effort HTML.
 *
 * All access is on the GeckoView UI thread; one fetch runs at a time and [reset]
 * clears prior state so snapshots can't bleed across jobs.
 */
class HtmlExtractor(private val logger: AppLogger) {

    data class Page(val url: String, val title: String?, val html: String, val final: Boolean, val matchedRule: Int = -1)

    private var latest: Page? = null
    private var anyWaiter: CompletableDeferred<Page>? = null
    private var finalWaiter: CompletableDeferred<Page>? = null

    fun reset() {
        latest = null
        anyWaiter = null
        finalWaiter = null
    }

    fun onPage(url: String, title: String?, html: String, final: Boolean, matchedRule: Int = -1) {
        logger.info("browser.extract_page", mapOf("bytes" to html.length, "final" to final, "matchedRule" to matchedRule))
        val page = Page(url, title, html, final, matchedRule)
        latest = page
        anyWaiter?.complete(page)
        anyWaiter = null
        if (final) {
            finalWaiter?.complete(page)
            finalWaiter = null
        }
    }

    /** Waits up to [timeoutMs] for any snapshot (used for domcontentloaded). */
    suspend fun awaitAny(timeoutMs: Long): Page? {
        latest?.let { return it }
        val def = CompletableDeferred<Page>()
        anyWaiter = def
        val res = withTimeoutOrNull(timeoutMs) { def.await() }
        if (anyWaiter === def) anyWaiter = null
        if (res == null) logger.warn("browser.extract_timeout")
        return res
    }

    /** Waits up to [timeoutMs] for the final (condition-met) snapshot; null on timeout. */
    suspend fun awaitFinal(timeoutMs: Long): Page? {
        latest?.let { if (it.final) return it }
        val def = CompletableDeferred<Page>()
        finalWaiter = def
        val res = withTimeoutOrNull(timeoutMs) { def.await() }
        if (finalWaiter === def) finalWaiter = null
        if (res == null) logger.warn("browser.extract_timeout")
        return res
    }

    /** The most recent snapshot of any kind (used as the best-effort fallback). */
    fun latestPage(): Page? = latest
}
