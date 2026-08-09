package com.meerkly.android.browser

import android.content.Context
import com.meerkly.android.logging.AppLogger
import com.meerkly.android.model.NavigationResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.WebExtension
import java.time.Duration
import java.time.Instant

/**
 * Owns the single [GeckoRuntime] and the primary visible [GeckoSession]. Navigation is exposed as a
 * suspend function that resolves once the page settles, capturing the committed URL
 * (`onLocationChange`) and title (`onTitleChange`), or fails on timeout / crash.
 *
 * Completion is **debounced**, not fired on the first `onPageStop`: a redirect (e.g. a Google SERP)
 * shows up as `page_stop(success=false)` for the aborted load immediately followed by a fresh
 * `page_start` for the redirect target. Finalizing on the first stop would capture the aborted
 * attempt (no title). Instead, each `page_stop` schedules finalization after a short grace window,
 * and any subsequent `page_start` cancels it — so we finalize on the *last* stop once the page is
 * quiet, with the final page's title and URL.
 *
 * All Gecko delegate callbacks run on the UI thread; [scope] uses Main.immediate so the debounce
 * bookkeeping is serialized with them. Held as a process singleton (via the Application) so
 * rotation/recomposition reuse the same runtime and session.
 */
class GeckoBrowserManager(
    private val appContext: Context,
    private val logger: AppLogger,
    private val redirectGraceMs: Long = 700L,
) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private var runtime: GeckoRuntime? = null

    var session: GeckoSession? = null
        private set

    private var pending: CompletableDeferred<Boolean>? = null
    private var finalizeJob: Job? = null
    private var lastSuccess: Boolean = false
    private var lastUrl: String? = null
    private var lastTitle: String? = null

    private val extractor = HtmlExtractor(logger)

    /** wait_for spec for the in-flight job; handed to the content script on request. */
    @Volatile
    private var currentWaitFor: String = WaitDOMContentLoaded

    /** settle_ms for the in-flight job (0 = content-script default). */
    @Volatile
    private var currentSettleMs: Int = 0

    /** Capture options for the in-flight job (spec default: strip both). Local
     *  (non-gateway) navigations keep the full document via the parameter
     *  defaults on navigateAndExtract. */
    @Volatile
    private var currentIncludeScripts: Boolean = true

    @Volatile
    private var currentIncludeStyles: Boolean = true

    /** wait_rules for the in-flight job, as a JSON array string ("[]" = none). */
    @Volatile
    private var currentRules: String = "[]"

    /** detect_ms (wait_rules guard probe window) for the in-flight job (0 = default). */
    @Volatile
    private var currentDetectMs: Int = 0

    /** Result of a fetch job: navigation outcome + extracted HTML + wait flags. */
    data class FetchOutcome(val nav: NavigationResult, val html: String?, val waitTimedOut: Boolean, val matchedRule: Int = -1, val httpStatus: Int = 0, val format: String = "html")

    /** One object fulfils all three delegate roles for the primary session. */
    private val delegate = object :
        GeckoSession.ProgressDelegate,
        GeckoSession.NavigationDelegate,
        GeckoSession.ContentDelegate {

        override fun onPageStart(session: GeckoSession, url: String) {
            lastUrl = url
            // A (re)load started — cancel any pending finalize; this is a redirect continuation.
            finalizeJob?.cancel()
            logger.info("browser.page_start", mapOf("url" to url))
        }

        override fun onPageStop(session: GeckoSession, success: Boolean) {
            logger.info("browser.page_stop", mapOf("success" to success))
            lastSuccess = success
            scheduleFinalize()
        }

        override fun onLocationChange(
            session: GeckoSession,
            url: String?,
            perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
            hasUserGesture: Boolean,
        ) {
            if (url != null) lastUrl = url
        }

        override fun onTitleChange(session: GeckoSession, title: String?) {
            lastTitle = title
        }

        override fun onCrash(session: GeckoSession) {
            logger.error("browser.crash")
            recoverSession()
            finalizeNow(false)
        }

        override fun onKill(session: GeckoSession) {
            logger.warn("browser.kill")
            recoverSession()
            finalizeNow(false)
        }
    }

    /** Finalize after a quiet window; a later [onPageStart] cancels and reschedules this. */
    private fun scheduleFinalize() {
        finalizeJob?.cancel()
        finalizeJob = scope.launch {
            delay(redirectGraceMs)
            finalizeNow(lastSuccess)
        }
    }

    private fun finalizeNow(success: Boolean) {
        finalizeJob?.cancel()
        pending?.let { if (!it.isCompleted) it.complete(success) }
    }

    fun start() {
        if (session != null) return
        val rt = GeckoRuntime.getDefault(appContext).also { runtime = it }
        installExtractor(rt)
        session = newSession(rt)
        logger.info("browser.started", mapOf("geckoview" to org.mozilla.geckoview.BuildConfig.MOZILLA_VERSION))
    }

    /** Loads the bundled HTML-extractor WebExtension and wires its native port. */
    private fun installExtractor(rt: GeckoRuntime) {
        rt.webExtensionController
            .ensureBuiltIn(EXTENSION_URI, EXTENSION_ID)
            .accept(
                { ext -> ext?.let { registerExtractorPort(it) } },
                { e -> logger.error("browser.extractor_failed", mapOf("error" to (e?.message ?: "unknown"))) },
            )
    }

    private fun registerExtractorPort(ext: WebExtension) {
        // HTML extraction path: content script -> background (runtime.sendMessage)
        // -> background runtime.sendNativeMessage("meerkly", …) -> here. Two GeckoView
        // constraints make this the only working shape (see assets/extensions/extractor):
        //   1. The manifest MUST declare the `geckoViewAddons` permission, else native
        //      messaging falls through to native manifests ("not supported on android").
        //   2. The native send MUST come from the persistent background, not the page's
        //      content context — a content-context send dies with "context unloaded"
        //      because the headless page is torn down before the round-trip completes.
        //   connectNative (a Port) is unsupported on Android; use sendNativeMessage.
        ext.setMessageDelegate(
            object : WebExtension.MessageDelegate {
                override fun onMessage(
                    nativeApp: String,
                    message: Any,
                    sender: WebExtension.MessageSender,
                ): GeckoResult<Any>? {
                    val o = message as? JSONObject ?: return null
                    when (o.optString("kind")) {
                        // app -> content: hand the content script this job's wait spec.
                        // Hand the content script this job's spec as a JSON *string* (not a
                        // JSONObject): object replies don't serialize back across the
                        // content-process boundary (they fail with "Invalid event data"), but a
                        // string does — the content script JSON.parses it.
                        "spec" -> {
                            val reply = JSONObject()
                                .put("waitFor", currentWaitFor)
                                .put("settleMs", currentSettleMs)
                                .put("waitRules", JSONArray(currentRules))
                                .put("detectMs", currentDetectMs)
                                .put("includeScripts", currentIncludeScripts)
                                .put("includeStyles", currentIncludeStyles)
                            return GeckoResult.fromValue(reply.toString() as Any)
                        }
                        "page" -> extractor.onPage(
                            url = o.optString("url"),
                            title = o.optString("title").ifEmpty { null },
                            html = o.optString("html"),
                            final = o.optBoolean("final", true),
                            matchedRule = o.optInt("matchedRule", -1),
                            httpStatus = o.optInt("httpStatus", 0),
                            format = o.optString("format", "html"),
                        )
                    }
                    return null
                }
            },
            "meerkly",
        )
        logger.info("browser.extractor_installed")
    }

    private fun newSession(rt: GeckoRuntime): GeckoSession {
        val settings = GeckoSessionSettings.Builder()
            .usePrivateMode(false)
            .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .build()
        return GeckoSession(settings).apply {
            setProgressDelegate(delegate)
            setNavigationDelegate(delegate)
            setContentDelegate(delegate)
            open(rt)
            // No GeckoView surface is attached (headless worker). Mark the session
            // active so its content process keeps high priority and is not discarded
            // mid-wait — otherwise delayed captures (selector/networkidle) lose their
            // content context before they can send the HTML.
            setActive(true)
        }
    }

    /** Loads [url] and suspends until the page settles, fails, crashes, or [timeoutMs] elapses. */
    suspend fun navigate(url: String, timeoutMs: Long = 30_000L): NavigationResult {
        val s = session ?: run { start(); session!! }
        val started = Instant.now()
        finalizeJob?.cancel()
        lastUrl = url
        lastTitle = null
        lastSuccess = false

        val deferred = CompletableDeferred<Boolean>()
        pending = deferred
        logger.info("browser.load", mapOf("url" to url))
        s.load(GeckoSession.Loader().uri(url))

        val success = withTimeoutOrNull(timeoutMs) { deferred.await() }
        finalizeJob?.cancel()
        pending = null
        val finished = Instant.now()

        return if (success == null) {
            NavigationResult(
                success = false,
                requestedUrl = url,
                finalUrl = lastUrl,
                title = lastTitle,
                error = "Navigation timeout after $timeoutMs ms",
                startedAt = started,
                finishedAt = finished,
                loadedMs = null,
                htmlSizeBytes = null,
            )
        } else {
            NavigationResult(
                success = success,
                requestedUrl = url,
                finalUrl = lastUrl,
                title = lastTitle,
                error = if (success) null else "Navigation failed",
                startedAt = started,
                finishedAt = finished,
                loadedMs = Duration.between(started, finished).toMillis(),
                htmlSizeBytes = null,
            )
        }
    }

    /**
     * Navigates to [url], waits per [waitFor] (the content script applies the
     * spec it requests via the `spec` message), and returns the extracted HTML.
     * The page-wait runs concurrently with navigation so the total stays within
     * [timeoutMs]. [FetchOutcome.html] is null only if extraction never produced a
     * page (e.g. the extension broke); a wait timeout still returns best-effort
     * HTML with [FetchOutcome.waitTimedOut] = true.
     */
    suspend fun navigateAndExtract(
        url: String,
        waitFor: String,
        settleMs: Int = 0,
        rules: String = "[]",
        detectMs: Int = 0,
        includeScripts: Boolean = true,
        includeStyles: Boolean = true,
        timeoutMs: Long = 30_000L,
    ): FetchOutcome = coroutineScope {
        currentWaitFor = waitFor
        currentSettleMs = settleMs
        currentRules = rules
        currentDetectMs = detectMs
        currentIncludeScripts = includeScripts
        currentIncludeStyles = includeStyles
        extractor.reset()
        val hasRules = runCatching { JSONArray(rules).length() > 0 }.getOrDefault(false)
        // domcontentloaded is satisfied by the early snapshot; the waiting modes —
        // and any wait_rules path (rule target or settle fallback) — need the
        // condition-met "final" snapshot (falling back to the early one).
        val wantFinal = hasRules || waitFor != WaitDOMContentLoaded
        val pageJob = async { if (wantFinal) extractor.awaitFinal(timeoutMs) else extractor.awaitAny(timeoutMs) }
        val nav = navigate(url, timeoutMs)
        if (!nav.success) {
            pageJob.cancel()
            return@coroutineScope FetchOutcome(nav, null, false)
        }
        val got = pageJob.await()
        val page = got ?: extractor.latestPage()
        val html = page?.html
        val result = if (html != null) {
            nav.copy(htmlSizeBytes = html.toByteArray(Charsets.UTF_8).size.toLong())
        } else {
            nav
        }
        FetchOutcome(result, html, wantFinal && got == null && html != null, page?.matchedRule ?: -1, page?.httpStatus ?: 0, page?.format ?: "html")
    }

    fun stopLoading() {
        session?.stop()
    }

    fun reload() {
        session?.reload()
    }

    /** After a crash/kill the session is closed and unusable; reopen a fresh one against the runtime. */
    private fun recoverSession() {
        val rt = runtime ?: return
        runCatching { session?.close() }
        session = newSession(rt)
        logger.info("browser.session_recovered")
    }

    private companion object {
        const val EXTENSION_URI = "resource://android/assets/extensions/extractor/"
        const val EXTENSION_ID = "extractor@meerkly"
        const val WaitDOMContentLoaded = "domcontentloaded"
    }
}
