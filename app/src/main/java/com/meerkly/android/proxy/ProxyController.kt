package com.meerkly.android.proxy

import android.os.Build
import com.meerkly.android.logging.AppLogger
import com.meerkly.sdk.ProxyConfig
import com.meerkly.sdk.ProxyException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
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

    // Volatile: emitState() reads these from the poll job's coroutine, which
    // may run on a different Dispatchers.Default thread than whichever thread
    // last wrote them under lifecycle.withLock. Without this, that thread has
    // no guarantee of ever observing the write.
    @Volatile private var client: ProxyHandle? = null
    @Volatile private var pollJob: Job? = null

    // start() and stop() suspend, so a synchronized block is wrong: a monitor
    // held across a suspension point blocks the thread the coroutine resumes
    // on. A Mutex is the coroutine equivalent, and is what keeps a start racing
    // a stop from leaving two clients behind.
    private val lifecycle = Mutex()

    /** Connect, if a publisher id is known. No-op when already running. */
    fun start() {
        scope.launch { startNow() }
    }

    private suspend fun startNow() = lifecycle.withLock { startLocked() }

    /**
     * The body of [startNow], with the lifecycle lock assumed held.
     *
     * Split out for [restartNow], which has to stop and start as one
     * indivisible step: `Mutex` is not reentrant, so a restart that called the
     * locking versions would deadlock, and one that took the lock twice would
     * leave a window where a concurrent [start] observes a controller with no
     * client and builds a second one.
     */
    private suspend fun startLocked() {
        if (client != null) return
        val id = publisherId()
        if (id.isNullOrBlank()) {
            logger.info("proxy.start_deferred", mapOf("reason" to "no publisher id"))
            return
        }

        // Inside the try, not before it. ProxyClient's constructor validates the
        // config and THROWS — an empty gatewayAddresses list is rejected there,
        // not at start(). Building the handle outside this block let that escape
        // as an uncaught exception in a launched coroutine, which kills the
        // process: the app crashed on the first sign-in rather than showing a
        // failure. A config error the user could act on must reach the UI as
        // Failed, never as a crash.
        val created = try {
            createHandle(
                ProxyConfig(
                    publisherId = id,
                    gatewayAddresses = gatewayAddresses,
                    deviceId = deviceId,
                    deviceName = deviceName,
                    sdk = "kotlin",
                    app = "meerkly-android/$appVersion",
                ),
            )
        } catch (e: ProxyException) {
            val reason = e.reasonText()
            logger.warn("proxy.config_rejected", mapOf("error" to reason))
            _lastError.value = reason
            _state.value = ProxyState.Failed
            return
        }
        client = created
        _lastError.value = null
        _state.value = ProxyState.Connecting
        startPolling()

        try {
            created.start()
            logger.info("proxy.connected", mapOf("client_key" to created.clientKey()))
        } catch (e: ProxyException) {
            val reason = e.reasonText()
            logger.warn("proxy.start_failed", mapOf("error" to reason))
            _lastError.value = reason
            _state.value = ProxyState.Failed
            stopPolling()
            created.destroy()
            client = null
            return
        }
        emitState()
    }

    /** Disconnect and release the native handle. Safe to call when stopped. */
    fun stop() {
        scope.launch { shutdown() }
    }

    suspend fun shutdown() = lifecycle.withLock { shutdownLocked() }

    /** The body of [shutdown], with the lifecycle lock assumed held. */
    private suspend fun shutdownLocked() {
        val c = client ?: run {
            _state.value = ProxyState.Stopped
            return
        }
        // Order matters here, and each step depends on the one before it:
        //  1. stopPolling() cancels AND joins the poll job, so by the time it
        //     returns no poll tick is in flight anywhere.
        //  2. Only once that is guaranteed is it safe to c.stop() / c.destroy()
        //     the handle — a poll tick still running could otherwise call into
        //     a handle mid-teardown or already destroyed (the SDK throws, and
        //     an uncaught throw in a launched coroutine is fatal).
        //  3. client is nulled only after destroy(), so no other lifecycle
        //     caller can observe a handle that is still half torn down.
        //  4. _state.value is set to Stopped last, so a poll tick joined in
        //     step 1 cannot have overwritten it afterwards.
        // The poll job never acquires `lifecycle` itself, so joining it here
        // from inside withLock cannot deadlock — do not "simplify" this back
        // to a bare cancel().
        stopPolling()
        try {
            c.stop()
        } catch (e: ProxyException) {
            logger.warn("proxy.stop_failed", mapOf("error" to e.reasonText()))
        } finally {
            c.destroy()
            client = null
            _state.value = ProxyState.Stopped
            logger.info("proxy.stopped")
        }
    }

    /**
     * Drop the connection and make a fresh one.
     *
     * A QUIC connection survives its device changing network: the tunnel
     * migrates onto the new path and keeps carrying traffic, so no handshake
     * happens and nothing re-reports where this device now leaves from. A
     * reconnect is how the client forces that to be re-established.
     *
     * A no-op when nothing is running, which is what keeps a network broadcast
     * from starting a worker the user switched off. Stop and start are one
     * locked step, so a restart racing a [start] cannot leave two clients
     * behind.
     */
    fun restart() {
        scope.launch { restartNow() }
    }

    suspend fun restartNow() = lifecycle.withLock {
        if (client == null) {
            logger.info("proxy.restart_skipped", mapOf("reason" to "not running"))
            return@withLock
        }
        logger.info("proxy.restarting")
        shutdownLocked()
        startLocked()
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

    /**
     * Cancel the poll AND wait for it to actually stop.
     *
     * cancel() alone is cooperative: a tick already in flight on another thread
     * keeps running, and it holds a reference to the handle we are about to
     * destroy. Joining is what makes "the poll is stopped" true before
     * destroy() rather than merely requested — without it the poll can call
     * into a released handle (the SDK throws, and an uncaught throw in a
     * launched coroutine is fatal) or overwrite Stopped with a stale reading.
     */
    private suspend fun stopPolling() {
        pollJob?.cancelAndJoin()
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

    /**
     * `reason` is the field; uniffi cannot generate a Rust field named
     * `message` because the Kotlin error already declares one. Both catch
     * sites need this same unwrapping — extracted so they cannot drift apart
     * again (a bare `e.message` on `ProxyException.Failed` renders as the
     * unhelpful `"reason=<reason>"`).
     */
    private fun ProxyException.reasonText(): String? =
        (this as? ProxyException.Failed)?.reason ?: message

    private companion object {
        const val POLL_INTERVAL_MS = 1_000L
    }
}
