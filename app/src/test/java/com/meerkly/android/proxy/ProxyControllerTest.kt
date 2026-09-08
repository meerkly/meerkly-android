package com.meerkly.android.proxy

import com.meerkly.android.logging.AppLogger
import com.meerkly.android.model.LogEntry
import com.meerkly.sdk.ClientState
import com.meerkly.sdk.ProxyConfig
import com.meerkly.sdk.ProxyException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
