package com.meerkly.android.browser

import android.content.Context
import com.meerkly.android.logging.AppLogger
import com.meerkly.android.model.NavigationResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
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
        session = newSession(rt)
        logger.info("browser.started", mapOf("geckoview" to org.mozilla.geckoview.BuildConfig.MOZILLA_VERSION))
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
}
