package com.meerkly.android.gateway

import android.app.Application
import com.meerkly.android.browser.GeckoBrowserManager
import com.meerkly.android.data.NoopLogger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.QueueDispatcher
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlinx.coroutines.flow.first

/**
 * The dashboard's "Connected" indicator is only honest if the client actually
 * tracks the socket. These pin the state machine: a worker counts as connected
 * once the gateway ACKS the register (being in the pool is what earns), not
 * merely once TCP is up.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GatewayConnectionStateTest {

    private lateinit var server: MockWebServer

    /** Server side of the upgraded socket; must be closed before shutdown or
     *  MockWebServer can't drain its queue ("Gave up waiting for queue to shut down"). */
    @Volatile
    private var serverSocket: WebSocket? = null

    /** Tracked so teardown can always stop it — including when a test fails
     *  mid-way and never reaches its own stop() call. A client left holding the
     *  socket is what makes MockWebServer.shutdown() hang. */
    private var gateway: GatewayClient? = null

    private fun client(url: String, token: String? = "tok-1"): GatewayClient {
        val app = RuntimeEnvironment.getApplication() as Application
        return GatewayClient(
            appContext = app,
            machineId = "m-1",
            geckoVersion = "152",
            logger = NoopLogger(),
            browserManager = GeckoBrowserManager(app, NoopLogger()),
            url = url,
            getDeviceToken = { token },
        ).also { gateway = it }
    }

    @After
    fun tearDown() {
        // Client first: it owns the live connection, and MockWebServer can't
        // drain its queue while one is still open.
        gateway?.stop()
        gateway = null
        serverSocket?.close(1000, "test over")
        serverSocket = null
        // Even with both ends closed, shutdown() waits on its dispatcher queue
        // and can exceed its internal deadline on a loaded CI runner. Every
        // assertion has already run by here, so teardown noise must not be able
        // to fail the test — it reports as a bug in behaviour that is fine.
        if (::server.isInitialized) runCatching { server.shutdown() }
    }

    /** Boots a gateway that accepts the socket and replies with whatever [onRegister] returns. */
    private fun startServer(onRegister: (String) -> String?) {
        server = MockWebServer()
        val dispatcher = QueueDispatcher()
        dispatcher.enqueueResponse(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                    serverSocket = webSocket
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    onRegister(text)?.let { webSocket.send(it) }
                }
            }),
        )
        server.dispatcher = dispatcher
        server.start()
    }

    private fun wsUrl(): String = server.url("/v1/connect").toString().replace("http://", "ws://")

    private suspend fun GatewayClient.awaitState(want: WorkerConnection) =
        withTimeout(5_000) { connection.first { it == want } }

    @Test
    fun `no gateway url configured reports disabled, never connected`() = runBlocking {
        val c = client(url = "")
        assertEquals(WorkerConnection.Disabled, c.connection.value)
        c.start()
        assertEquals(WorkerConnection.Disabled, c.connection.value)
    }

    @Test
    fun `starts disconnected rather than optimistically connected`() {
        val c = client(url = "ws://127.0.0.1:1/v1/connect")
        assertEquals(WorkerConnection.Disconnected, c.connection.value)
    }

    @Test
    fun `an unreachable gateway reports offline, not connected`() = runBlocking {
        // Port 1 is guaranteed closed for a normal user process.
        val c = client(url = "ws://127.0.0.1:1/v1/connect")
        c.start()
        c.awaitState(WorkerConnection.Offline)
        assertEquals(WorkerConnection.Offline, c.connection.value)
        c.stop()
    }

    @Test
    fun `an open socket is not connected until the gateway acks the register`() = runBlocking {
        // Accepts the socket but never sends `registered` — the worker is not
        // in the dispatch pool, so it must not claim to be connected.
        startServer { null }
        val c = client(wsUrl())
        c.start()
        c.awaitState(WorkerConnection.Registering)
        assertEquals(WorkerConnection.Registering, c.connection.value)
        c.stop()
    }

    @Test
    fun `registered ack promotes the worker to connected`() = runBlocking {
        startServer { """{"type":"registered","connectionId":"c-1","heartbeatSec":30}""" }
        val c = client(wsUrl())
        c.start()
        c.awaitState(WorkerConnection.Connected)
        assertEquals(WorkerConnection.Connected, c.connection.value)
        c.stop()
    }

    @Test
    fun `a terminal auth rejection reports unpaired, not connected`() = runBlocking {
        startServer { """{"type":"error","code":"device_auth_failed","message":"unknown device"}""" }
        val c = client(wsUrl())
        c.start()
        c.awaitState(WorkerConnection.Unpaired)
        assertEquals(WorkerConnection.Unpaired, c.connection.value)
        c.stop()
    }

    @Test
    fun `a retryable verification failure stays offline rather than unpaired`() = runBlocking {
        startServer { """{"type":"error","code":"verification_unavailable","message":"account down"}""" }
        val c = client(wsUrl())
        c.start()
        c.awaitState(WorkerConnection.Offline)
        assertEquals(WorkerConnection.Offline, c.connection.value)
        c.stop()
    }
}
