package com.meerkly.android.proxy

import android.os.Build
import com.meerkly.android.logging.AppLogger
import com.meerkly.sdk.ProxyConfig
import com.meerkly.sdk.ProxyException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The process's single exit-node client.
 *
 * Replaces GatewayClient. Where that spoke the fetch-job protocol itself, this
 * hands a publisher id to the SDK and reports what the SDK is doing — the
 * transport, the reconnect backoff and the proxying all live in the Rust core
 * now.
 *
 * One client per process, enforced by [lifecycle]. Two would register twice
 * under one publisher id and compete for the same bandwidth.
 *
 * @param publisherId read fresh on every start, never captured: it arrives
 *   asynchronously after sign-in, and a controller built at app start would
 *   otherwise hold the null it saw then.
 */
// internal, not public: the primary constructor's createHandle parameter
// exposes ProxyHandle, which is deliberately internal (see ProxyHandle.kt) —
// Kotlin's visibility checker requires the enclosing declaration to be no
// wider than that. The whole app lives in one Gradle module, so this loses
// nothing: Tasks 5-9 construct this from elsewhere in the same module.
internal class ProxyController(
    private val deviceId: String,
    private val deviceName: String = Build.MODEL,
    private val appVersion: String,
    private val logger: AppLogger,
    private val publisherId: () -> String?,
    private val gatewayAddresses: List<String> = emptyList(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    // The seam that makes this class testable off-device: the real handle's
    // constructor loads a native library a JVM test cannot supply.
    private val createHandle: (ProxyConfig) -> ProxyHandle = { SdkProxyHandle(it) },
) {
    private val _state = MutableStateFlow(ProxyState.Disconnected)
    val state: StateFlow<ProxyState> = _state.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var client: ProxyHandle? = null
    private var pollJob: Job? = null

    // start() and stop() suspend, so a synchronized block is wrong: a monitor
    // held across a suspension point blocks the thread the coroutine resumes
    // on. A Mutex is the coroutine equivalent, and is what keeps a start racing
    // a stop from leaving two clients behind.
    private val lifecycle = Mutex()

    /** Connect, if a publisher id is known. No-op when already running. */
    fun start() {
        scope.launch { startNow() }
    }

    private suspend fun startNow() = lifecycle.withLock {
        if (client != null) return@withLock
        val id = publisherId()
        if (id.isNullOrBlank()) {
            logger.info("proxy.start_deferred", mapOf("reason" to "no publisher id"))
            return@withLock
        }

        val created = createHandle(
            ProxyConfig(
                publisherId = id,
                gatewayAddresses = gatewayAddresses,
                deviceId = deviceId,
                deviceName = deviceName,
                sdk = "kotlin",
                app = "meerkly-android/$appVersion",
            ),
        )
        client = created
        _lastError.value = null
        _state.value = ProxyState.Connecting
        startPolling()

        try {
            created.start()
            logger.info("proxy.connected", mapOf("client_key" to created.clientKey()))
        } catch (e: ProxyException) {
            // `reason` is the field; uniffi cannot generate a Rust field named
            // `message` because the Kotlin error already declares one.
            val reason = (e as? ProxyException.Failed)?.reason ?: e.message
            logger.warn("proxy.start_failed", mapOf("error" to reason))
            _lastError.value = reason
            _state.value = ProxyState.Failed
            stopPolling()
            created.destroy()
            client = null
            return@withLock
        }
        emitState()
    }

    /** Disconnect and release the native handle. Safe to call when stopped. */
    fun stop() {
        scope.launch { shutdown() }
    }

    suspend fun shutdown() = lifecycle.withLock {
        val c = client ?: run {
            _state.value = ProxyState.Stopped
            return@withLock
        }
        stopPolling()
        try {
            c.stop()
        } catch (e: ProxyException) {
            logger.warn("proxy.stop_failed", mapOf("error" to e.message))
        } finally {
            c.destroy()
            client = null
            _state.value = ProxyState.Stopped
            logger.info("proxy.stopped")
        }
    }

    /**
     * The SDK exposes state as plain getters with no callback or listener, so
     * something has to poll it. Once a second, emitting only on change, so a
     * connected device does no work and the notification is not rewritten.
     */
    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                emitState()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun emitState() {
        val c = client ?: return
        val next = ProxyState.from(c.state())
        if (_state.value != next) {
            _state.value = next
            logger.info("proxy.state", mapOf("state" to next.name))
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 1_000L
    }
}
