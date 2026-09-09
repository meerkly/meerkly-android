package com.meerkly.android.proxy

import com.meerkly.android.logging.AppLogger
import com.meerkly.android.model.LogEntry
import com.meerkly.sdk.ClientState
import com.meerkly.sdk.ProxyConfig
import com.meerkly.sdk.ProxyException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProxyControllerTest {

    /** Records what the controller asked of the SDK, and answers as told. */
    private class FakeHandle(
        var state: ClientState = ClientState.IDLE,
        val failWith: String? = null,
    ) : ProxyHandle {
        var started = 0
        var stopped = 0
        var destroyed = 0
        var config: ProxyConfig? = null

        override fun state() = state
        override fun clientKey(): String? = "acct:inst"
        override suspend fun start() {
            started++
            failWith?.let { throw ProxyException.Failed(it) }
            state = ClientState.CONNECTED
        }
        override suspend fun stop() { stopped++; state = ClientState.STOPPED }
        override fun destroy() { destroyed++ }
    }

    /**
     * A [ProxyHandle] whose [stop] genuinely suspends, so a poll tick has a
     * real window to run (or fail to be stopped) while shutdown is in
     * flight. [FakeHandle]'s methods never suspend, which is exactly why
     * `StandardTestDispatcher` could never exercise the locking in Finding 1.
     */
    private class SlowStopHandle(
        var state: ClientState = ClientState.IDLE,
    ) : ProxyHandle {
        var started = 0
        var stopped = 0
        var destroyed = 0
        var config: ProxyConfig? = null

        override fun state() = state
        override fun clientKey(): String? = "acct:inst"
        override suspend fun start() {
            started++
            state = ClientState.CONNECTED
        }
        override suspend fun stop() {
            delay(STOP_DELAY_MS)
            stopped++
            state = ClientState.STOPPED
        }
        override fun destroy() { destroyed++ }

        companion object {
            const val STOP_DELAY_MS = 5_000L
        }
    }

    // AppLogger declares info/warn/error plus recentEntries with no default
    // implementation, so all four must be overridden here even though this
    // test only cares about info/warn.
    private val logger = object : AppLogger {
        override fun info(event: String, data: Map<String, Any?>) {}
        override fun warn(event: String, data: Map<String, Any?>) {}
        override fun error(event: String, data: Map<String, Any?>) {}
        override val recentEntries: StateFlow<List<LogEntry>> = MutableStateFlow(emptyList())
    }

    private fun controller(
        handle: FakeHandle,
        publisherId: String? = "pub_abc",
        scope: TestScope,
    ) = ProxyController(
        deviceId = "dev_test",
        deviceName = "Test",
        appVersion = "2.0.0",
        logger = logger,
        publisherId = { publisherId },
        scope = scope,
        createHandle = { handle.also { h -> h.config = it } },
    )

    @Test
    fun `start reaches Connected and reports the config the SDK needs`() = runTest {
        val handle = FakeHandle()
        val controller = controller(handle, scope = TestScope(StandardTestDispatcher(testScheduler)))

        controller.start()
        advanceTimeBy(100)

        assertEquals(ProxyState.Connected, controller.state.value)
        assertEquals(1, handle.started)
        assertEquals("pub_abc", handle.config?.publisherId)
        assertEquals("dev_test", handle.config?.deviceId)
        assertEquals("kotlin", handle.config?.sdk)
        assertEquals("meerkly-android/2.0.0", handle.config?.app)

        // A started controller's poll job re-schedules itself every second
        // forever. Left running, runTest's own end-of-test drain — it shares
        // this test's testScheduler across every TestScope built from it —
        // never reaches "idle" and hangs for real, not just virtually.
        controller.shutdown()
    }

    @Test
    fun `no publisher id means no client is ever built`() = runTest {
        val handle = FakeHandle()
        val controller = controller(handle, publisherId = null, scope = TestScope(StandardTestDispatcher(testScheduler)))

        controller.start()
        advanceTimeBy(100)

        assertEquals(0, handle.started)
        assertEquals(ProxyState.Disconnected, controller.state.value)
    }

    @Test
    fun `a failed start surfaces the reason and releases the handle`() = runTest {
        val handle = FakeHandle(failWith = "gateway refused")
        val controller = controller(handle, scope = TestScope(StandardTestDispatcher(testScheduler)))

        controller.start()
        advanceTimeBy(100)

        assertEquals(ProxyState.Failed, controller.state.value)
        assertEquals("gateway refused", controller.lastError.value)
        // The native handle must not leak just because connecting failed.
        assertEquals(1, handle.destroyed)
    }

    @Test
    fun `starting twice builds one client`() = runTest {
        val handle = FakeHandle()
        val controller = controller(handle, scope = TestScope(StandardTestDispatcher(testScheduler)))

        controller.start()
        controller.start()
        advanceTimeBy(100)

        assertEquals("a process is one exit node", 1, handle.started)

        // See the note in the first test: an un-cancelled poll job hangs
        // runTest's own cleanup, which shares this scheduler.
        controller.shutdown()
    }

    @Test
    fun `the poll follows the SDK's state while running`() = runTest {
        val handle = FakeHandle()
        val controller = controller(handle, scope = TestScope(StandardTestDispatcher(testScheduler)))

        controller.start()
        advanceTimeBy(100)
        assertEquals(ProxyState.Connected, controller.state.value)

        // The SDK dropped the connection on its own; the poll must notice.
        handle.state = ClientState.CONNECTING
        advanceTimeBy(1_500)

        assertEquals(ProxyState.Connecting, controller.state.value)

        // See the note in the first test: an un-cancelled poll job hangs
        // runTest's own cleanup, which shares this scheduler.
        controller.shutdown()
    }

    @Test
    fun `shutdown stops the client and the poll`() = runTest {
        val handle = FakeHandle()
        val controller = controller(handle, scope = TestScope(StandardTestDispatcher(testScheduler)))

        controller.start()
        advanceTimeBy(100)
        controller.shutdown()

        assertEquals(ProxyState.Stopped, controller.state.value)
        assertEquals(1, handle.stopped)
        assertEquals(1, handle.destroyed)

        // A poll still running would overwrite Stopped with the fake's state.
        handle.state = ClientState.CONNECTED
        advanceTimeBy(3_000)
        assertEquals(ProxyState.Stopped, controller.state.value)
    }

    /**
     * Finding 1: `stopPolling()` must cancelAndJoin(), not just cancel(), or
     * `shutdown()` can destroy the handle — and set Stopped — while a poll
     * tick is still (or about to be) live. Nothing above catches this,
     * because [FakeHandle]'s methods never suspend, so `StandardTestDispatcher`
     * never has to interleave the poll with a shutdown in progress.
     *
     * [SlowStopHandle.stop] suspends, which holds `shutdown()` open inside
     * `lifecycle.withLock` for a while. During that window we advance the
     * scheduler well past the poll interval — the window in which a
     * cancel()-only poll job (the pre-fix behavior) is not yet guaranteed to
     * have stopped, so a tick could still observe the not-yet-destroyed
     * handle and rewrite `_state` out from under the eventual Stopped value.
     * A joined poll cannot do this, because `stopPolling()` does not return
     * until the poll job has actually finished.
     */
    @Test
    fun `shutdown joins the poll before destroying the handle`() = runTest {
        val handle = SlowStopHandle()
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val controller = ProxyController(
            deviceId = "dev_test",
            deviceName = "Test",
            appVersion = "2.0.0",
            logger = logger,
            publisherId = { "pub_abc" },
            scope = scope,
            createHandle = { handle.also { h -> h.config = it } },
        )

        controller.start()
        advanceTimeBy(100)
        assertEquals(ProxyState.Connected, controller.state.value)

        val shutdownJob = launch { controller.shutdown() }
        // Let shutdown() acquire the lock, request the poll's cancellation
        // and start c.stop() -- but c.stop() will not resolve yet.
        runCurrent()
        assertEquals(0, handle.destroyed)
        assertEquals(ProxyState.Connected, controller.state.value)

        // While stop() is still suspended, advance well past the poll
        // interval -- the window a poll tick would need to run again if the
        // poll were merely cancel()led rather than actually joined.
        handle.state = ClientState.CONNECTED
        advanceTimeBy(1_500)

        // Let stop()'s delay elapse and shutdown() run to completion.
        advanceUntilIdle()
        shutdownJob.join()

        assertEquals("stop() is called exactly once", 1, handle.stopped)
        assertEquals("destroy() is called exactly once", 1, handle.destroyed)
        // The value must be Stopped, and must not have been overwritten by a
        // stale poll reading taken before the handle was torn down.
        assertEquals(ProxyState.Stopped, controller.state.value)
    }

    /**
     * The reason this exists: a QUIC connection survives its device changing
     * network, so the gateway keeps serving traffic from an address nothing
     * re-reports. Forcing a fresh handshake is what makes the new network
     * visible from the client side.
     */
    @Test
    fun `restart tears the client down and brings it back`() = runTest {
        val handle = FakeHandle()
        val controller = controller(handle, scope = TestScope(StandardTestDispatcher(testScheduler)))

        controller.start()
        advanceTimeBy(100)
        assertEquals(ProxyState.Connected, controller.state.value)

        controller.restart()
        advanceTimeBy(100)

        assertEquals("the old client is stopped", 1, handle.stopped)
        assertEquals("and released", 1, handle.destroyed)
        assertEquals("before a new one is started", 2, handle.started)
        assertEquals(ProxyState.Connected, controller.state.value)

        controller.shutdown()
    }

    /**
     * A network change arrives whether or not the user is earning. Reconnecting
     * on one would start a worker they switched off, from a broadcast they
     * never asked for.
     */
    @Test
    fun `restart on a stopped controller stays stopped`() = runTest {
        val handle = FakeHandle()
        val controller = controller(handle, scope = TestScope(StandardTestDispatcher(testScheduler)))

        controller.restart()
        advanceTimeBy(100)

        assertEquals(0, handle.started)
        assertEquals(0, handle.stopped)
        assertEquals(0, handle.destroyed)
        assertEquals(ProxyState.Disconnected, controller.state.value)
    }

    @Test
    fun `shutdown on a controller that never started is not an error`() = runTest {
        val handle = FakeHandle()
        val controller = controller(handle, scope = TestScope(StandardTestDispatcher(testScheduler)))

        controller.shutdown()

        assertEquals(ProxyState.Stopped, controller.state.value)
        assertEquals(0, handle.destroyed)
        assertNull(controller.lastError.value)
        assertTrue(true)
    }
}
