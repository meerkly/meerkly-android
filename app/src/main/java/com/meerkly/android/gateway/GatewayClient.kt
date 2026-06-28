package com.meerkly.android.gateway

import android.content.Context
import android.os.Build
import com.meerkly.android.logging.AppLogger
import com.meerkly.android.util.MiniJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * Maintains a persistent two-way WebSocket to the Meerkly API gateway and registers this device as
 * a worker. Phase 1 only registers and keeps the connection alive: OkHttp auto-replies to the
 * gateway's pings (and sends its own via [pingInterval]), which is what refreshes the device's
 * presence in the gateway's Redis registry. Request forwarding arrives in a later phase.
 *
 * Held by the process-singleton [com.meerkly.android.AppGraph] so it survives Activity recreation.
 */
class GatewayClient(
    private val appContext: Context,
    private val machineId: String,
    private val geckoVersion: String?,
    private val logger: AppLogger,
    private val url: String,
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

    /** Open the connection and keep it alive, reconnecting with backoff on drop. */
    fun start() {
        if (url.isBlank()) {
            logger.info("gateway.disabled", mapOf("reason" to "no GATEWAY_URL configured"))
            return
        }
        stopped = false
        connect()
    }

    /** Close the connection for good. */
    fun stop() {
        stopped = true
        reconnectJob?.cancel()
        webSocket?.close(NORMAL_CLOSURE, "client stopping")
        webSocket = null
    }

    private fun connect() {
        if (stopped) return
        logger.info("gateway.connecting", mapOf("url" to url))
        webSocket = client.newWebSocket(Request.Builder().url(url).build(), listener)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(ws: WebSocket, response: Response) {
            backoffMs = INITIAL_BACKOFF_MS
            logger.info("gateway.open")
            ws.send(buildRegister())
        }

        override fun onMessage(ws: WebSocket, text: String) {
            logger.info("gateway.message", mapOf("text" to text))
        }

        override fun onClosing(ws: WebSocket, code: Int, reason: String) {
            ws.close(NORMAL_CLOSURE, null)
        }

        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
            logger.warn("gateway.closed", mapOf("code" to code, "reason" to reason))
            scheduleReconnect()
        }

        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
            logger.error("gateway.failure", mapOf("error" to t.message))
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (stopped || reconnectJob?.isActive == true) return
        val delayMs = backoffMs
        backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
        logger.info("gateway.reconnect_scheduled", mapOf("delayMs" to delayMs))
        reconnectJob = scope.launch {
            delay(delayMs)
            connect()
        }
    }

    private fun buildRegister(): String {
        val appVersion = runCatching {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
        }.getOrNull() ?: "?"
        return MiniJson.encode(
            mapOf(
                "type" to "register",
                "machineId" to machineId,
                "platform" to "android",
                "device" to mapOf(
                    "deviceModel" to "${Build.MANUFACTURER} ${Build.MODEL}",
                    "os" to "android ${Build.VERSION.RELEASE} (sdk ${Build.VERSION.SDK_INT})",
                    "arch" to (Build.SUPPORTED_ABIS.firstOrNull() ?: ""),
                    "appVersion" to appVersion,
                    "engineVersion" to "GeckoView ${geckoVersion ?: "unknown"}",
                ),
            ),
        )
    }

    companion object {
        private const val NORMAL_CLOSURE = 1000
        private const val INITIAL_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 30_000L
    }
}
