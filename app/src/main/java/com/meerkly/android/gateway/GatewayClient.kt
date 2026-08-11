package com.meerkly.android.gateway

import android.content.Context
import android.os.PowerManager
import com.meerkly.android.browser.GeckoBrowserManager
import com.meerkly.android.data.DeviceInfo
import com.meerkly.android.logging.AppLogger
import com.meerkly.android.model.NavigationResult
import com.meerkly.android.util.MiniJson
import com.meerkly.android.util.UrlValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Maintains a persistent two-way WebSocket to the Meerkly API gateway and registers this device as
 * a worker. OkHttp auto-replies to the gateway's pings (and sends its own via [pingInterval]),
 * which is what refreshes the device's presence in the gateway's Redis registry.
 *
 * The register frame must carry the device token issued at account pairing — the gateway rejects
 * unpaired workers with an `error` frame (`device_auth_failed` terminal, `verification_unavailable`
 * retryable) and closes. [getDeviceToken] is read on EVERY (re)connect so a rotated token is
 * picked up without a restart.
 *
 * Held by the process-singleton [com.meerkly.android.AppGraph] so it survives Activity recreation.
 */
class GatewayClient(
    private val appContext: Context,
    private val machineId: String,
    private val geckoVersion: String?,
    private val logger: AppLogger,
    /**
     * The one thing this client needs from the browser: run a crawl and hand
     * back the outcome. Injected as a function rather than the manager itself
     * so the whole fetch path can be exercised without a GeckoRuntime — the
     * absence of that seam is why a missing feed-recording call shipped
     * unnoticed.
     */
    private val fetchPage: suspend (
        url: String,
        waitFor: String,
        settleMs: Int,
        rulesJson: String,
        detectMs: Int,
        includeScripts: Boolean,
        includeStyles: Boolean,
    ) -> GeckoBrowserManager.FetchOutcome,
    private val url: String,
    private val getDeviceToken: () -> String? = { null },
    /**
     * Every finished crawl, success or failure, for the Activity feed. Defaulted
     * to a no-op so tests can construct the client without a repository — and
     * because for a long time nothing recorded these at all: the feed's ring was
     * only ever written by the debug URL tester, so gateway work was invisible.
     */
    private val onNavigation: (NavigationResult) -> Unit = {},
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var webSocket: WebSocket? = null
    private var backoffMs = INITIAL_BACKOFF_MS
    private var reconnectJob: Job? = null

    @Volatile
    private var stopped = false

    // Set on a terminal auth rejection (device_auth_failed): retrying with the
    // same token would fail again, so hold off until reconnect() (new token).
    @Volatile
    internal var paused = false
        private set

    /**
     * Live socket state for the UI. Starts [WorkerConnection.Disabled] when no
     * gateway is configured so a build that can never connect never claims to.
     */
    private val _connection = MutableStateFlow(
        if (url.isBlank()) WorkerConnection.Disabled else WorkerConnection.Disconnected,
    )
    val connection: StateFlow<WorkerConnection> = _connection.asStateFlow()

    /** Disabled is terminal — never let a transition talk a gateway-less build online. */
    private fun setConnection(state: WorkerConnection) {
        if (_connection.value != WorkerConnection.Disabled) _connection.value = state
    }

    /** Open the connection and keep it alive, reconnecting with backoff on drop. No-op if running. */
    fun start() {
        if (url.isBlank()) {
            logger.info("gateway.disabled", mapOf("reason" to "no GATEWAY_URL configured"))
            return
        }
        stopped = false
        if (webSocket != null || reconnectJob?.isActive == true) return
        connect()
    }

    /**
     * Drop any current connection/backoff and connect fresh — used after device
     * registration so the new token is presented immediately. Also clears an
     * auth-failure pause.
     */
    fun reconnect() {
        if (url.isBlank()) return
        stopped = false
        paused = false
        backoffMs = INITIAL_BACKOFF_MS
        reconnectJob?.cancel()
        reconnectJob = null
        // Null the field FIRST so the old socket's onClosed/onFailure see
        // themselves as stale and don't double-schedule.
        val old = webSocket
        webSocket = null
        old?.close(NORMAL_CLOSURE, "reconnecting")
        connect()
    }

    /** Close the connection for good. */
    fun stop() {
        stopped = true
        reconnectJob?.cancel()
        webSocket?.close(NORMAL_CLOSURE, "client stopping")
        webSocket = null
        setConnection(WorkerConnection.Disconnected)
    }

    private fun connect() {
        if (stopped || paused) return
        logger.info("gateway.connecting", mapOf("url" to url))
        setConnection(WorkerConnection.Connecting)
        webSocket = client.newWebSocket(Request.Builder().url(url).build(), listener)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(ws: WebSocket, response: Response) {
            backoffMs = INITIAL_BACKOFF_MS
            logger.info("gateway.open")
            // Open, but not in the dispatch pool until the gateway acks the
            // register below — don't report Connected yet.
            setConnection(WorkerConnection.Registering)
            ws.send(buildRegister(getDeviceToken()))
        }

        override fun onMessage(ws: WebSocket, text: String) {
            val msg = runCatching { JSONObject(text) }.getOrNull() ?: return
            when (msg.optString("type")) {
                "fetch" -> {
                    val job = FetchFrame.parse(msg)
                    if (job == null) {
                        logger.warn("gateway.fetch.malformed", mapOf("raw" to text))
                    } else {
                        handleFetch(ws, job)
                    }
                }
                "registered" -> {
                    logger.info("gateway.registered", mapOf("connectionId" to msg.optString("connectionId")))
                    setConnection(WorkerConnection.Connected)
                }
                "error" -> handleGatewayError(msg.optString("code"), msg.optString("message"))
                else -> logger.info("gateway.message", mapOf("type" to msg.optString("type")))
            }
        }

        override fun onClosing(ws: WebSocket, code: Int, reason: String) {
            ws.close(NORMAL_CLOSURE, null)
        }

        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
            if (ws !== webSocket) return // superseded by reconnect(); don't double-schedule
            logger.warn("gateway.closed", mapOf("code" to code, "reason" to reason))
            reportDown()
            scheduleReconnect()
        }

        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
            if (ws !== webSocket) return
            logger.error("gateway.failure", mapOf("error" to t.message))
            reportDown()
            scheduleReconnect()
        }
    }

    /**
     * The socket went away. A terminal auth rejection arrives as an `error`
     * frame *followed by* the gateway closing, so Unpaired must survive the
     * close that trails it — otherwise the UI would blame the network for what
     * is really an unpaired device.
     */
    private fun reportDown() {
        setConnection(if (paused) WorkerConnection.Unpaired else WorkerConnection.Offline)
    }

    /**
     * The gateway rejected this connection. device_auth_failed is terminal for
     * the current token (unpaired/invalid/revoked) — pause instead of hammering
     * the gateway; registration calls reconnect() with a fresh token.
     * verification_unavailable is transient, so normal backoff applies.
     */
    internal fun handleGatewayError(code: String, message: String) {
        logger.warn("gateway.rejected", mapOf("code" to code, "message" to message))
        if (isTerminalAuthError(code)) {
            paused = true
            setConnection(WorkerConnection.Unpaired)
            logger.warn("gateway.paused", mapOf("reason" to "device not paired / token invalid"))
        } else {
            // Retryable (verification_unavailable): the gateway closes after
            // this, and normal backoff applies.
            setConnection(WorkerConnection.Offline)
        }
    }

    private fun scheduleReconnect() {
        if (stopped || paused || reconnectJob?.isActive == true) return
        val delayMs = backoffMs
        backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
        logger.info("gateway.reconnect_scheduled", mapOf("delayMs" to delayMs))
        reconnectJob = scope.launch {
            delay(delayMs)
            connect()
        }
    }

    /** Run a crawl job on the GeckoView session and return the HTML to the gateway. */
    private fun handleFetch(ws: WebSocket, job: FetchJob) {
        // Defaults already applied by FetchFrame.parse (spec semantics).
        val (jobId, url, mode, settleMs, rules, detectMs, includeScripts, includeStyles) = job
        logger.info("gateway.fetch", mapOf("jobId" to jobId, "url" to url, "waitFor" to mode, "settleMs" to settleMs, "detectMs" to detectMs))
        // Screen-off crawls: the foreground service keeps the process alive, but
        // nothing else guarantees the CPU stays up through the 30s crawl window.
        // Scoped to the job with a hard timeout — never a standing wakelock.
        val wakeLock = runCatching {
            (appContext.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "meerkly:fetch")
                .apply { acquire(FETCH_WAKELOCK_MS) }
        }.getOrNull()
        // GeckoView session operations must run on the main thread.
        scope.launch(Dispatchers.Main) {
            try {
                // blockPrivateHosts: remotely-dispatched jobs must not probe this device's own network.
                UrlValidator.validateAndNormalize(url, blockPrivateHosts = true).fold(
                    onSuccess = { normalized ->
                        val outcome = fetchPage(
                            normalized, mode, settleMs, rules, detectMs,
                            includeScripts, includeStyles,
                        )
                        val nav = outcome.nav
                        // Record every finished crawl, success or failure — the
                        // Activity feed is the app's proof of work, and one that
                        // hid failures would be dishonest.
                        onNavigation(nav)
                        if (nav.success && outcome.html != null) {
                            sendResult(ws, jobId, true, nav.finalUrl, nav.title, outcome.html, null, nav.loadedMs, outcome.waitTimedOut, outcome.matchedRule, outcome.httpStatus, outcome.format)
                        } else {
                            val err = nav.error ?: "HTML extraction failed"
                            sendResult(ws, jobId, false, nav.finalUrl, nav.title, null, err, nav.loadedMs, false, -1, outcome.httpStatus)
                        }
                    },
                    onFailure = { e ->
                        val now = java.time.Instant.now()
                        onNavigation(
                            NavigationResult(
                                success = false, requestedUrl = url, finalUrl = null, title = null,
                                error = e.message ?: "Invalid URL", startedAt = now, finishedAt = now,
                                loadedMs = null, htmlSizeBytes = null,
                            ),
                        )
                        sendResult(ws, jobId, false, null, null, null, e.message ?: "Invalid URL", null, false, -1, 0)
                    },
                )
            } finally {
                runCatching { if (wakeLock?.isHeld == true) wakeLock.release() }
            }
        }
    }

    private fun sendResult(
        ws: WebSocket,
        jobId: String,
        success: Boolean,
        finalUrl: String?,
        title: String?,
        html: String?,
        error: String?,
        loadedMs: Long?,
        waitTimedOut: Boolean,
        matchedRule: Int,
        httpStatus: Int,
        format: String = "html",
    ) {
        ws.send(
            MiniJson.encode(
                mapOf(
                    "type" to "result",
                    "jobId" to jobId,
                    "success" to success,
                    "finalUrl" to finalUrl,
                    "title" to title,
                    "response" to html,
                    "format" to format,
                    "error" to error,
                    "loadedMs" to loadedMs,
                    "waitTimedOut" to waitTimedOut,
                    "matchedRule" to matchedRule,
                    "httpStatus" to httpStatus,
                ),
            ),
        )
    }

    private fun buildRegister(deviceToken: String?): String =
        buildRegisterFrame(machineId, DeviceInfo.collect(appContext, geckoVersion), deviceToken)

    companion object {
        private const val NORMAL_CLOSURE = 1000
        private const val INITIAL_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 30_000L

        /** Crawl budget (30s) + margin; hard cap so a leaked lock self-releases. */
        private const val FETCH_WAKELOCK_MS = 45_000L

        /** Terminal = retrying with the same token would fail again. */
        internal fun isTerminalAuthError(code: String) = code == "device_auth_failed"

        /** Pure frame builder (unit-testable without a Gecko runtime). */
        internal fun buildRegisterFrame(machineId: String, info: DeviceInfo, deviceToken: String?): String {
            val frame = mutableMapOf<String, Any?>(
                "type" to "register",
                "machineId" to machineId,
                "platform" to "android",
                "capabilities" to listOf("fetch"),
                "region" to "",
                "device" to mapOf(
                    "deviceModel" to info.deviceModel,
                    "os" to info.os,
                    "arch" to info.arch,
                    "appVersion" to info.appVersion,
                    "engineVersion" to info.engineVersion,
                    "cpuCores" to info.cpuCores,
                    "memoryMb" to info.memoryMb,
                    "screen" to info.screen,
                    "timezone" to info.timezone,
                    "locale" to info.locale,
                ),
            )
            if (!deviceToken.isNullOrBlank()) {
                frame["deviceToken"] = deviceToken
            }
            return MiniJson.encode(frame)
        }
    }
}
