package com.meerkly.android.gateway

import android.app.Application
import android.os.Looper
import com.meerkly.android.browser.GeckoBrowserManager
import com.meerkly.android.data.NoopLogger
import com.meerkly.android.model.NavigationResult
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.QueueDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Every finished crawl must reach the Activity feed.
 *
 * This exists because it didn't: the recording call was present on the
 * invalid-URL branch and missing on the success branch, so the feed stayed
 * empty while the worker was serving real traffic. Nothing caught it, because
 * `handleFetch` couldn't be driven without a GeckoRuntime. The crawl is now
 * injected as a function, which is what makes this test possible.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FetchRecordingTest {

    private lateinit var server: MockWebServer

    @Volatile private var serverSocket: WebSocket? = null
    private val recorded = CopyOnWriteArrayList<NavigationResult>()
    private var gateway: GatewayClient? = null

    @After
    fun tearDown() {
        gateway?.stop()
        serverSocket?.close(1000, "test over")
        if (::server.isInitialized) runCatching { server.shutdown() }
    }

    private fun nav(success: Boolean, url: String) = NavigationResult(
        success = success,
        requestedUrl = url,
        finalUrl = url,
        title = if (success) "Example" else null,
        error = if (success) null else "Navigation timeout after 30000 ms",
        startedAt = Instant.parse("2026-08-11T12:00:00Z"),
        finishedAt = Instant.parse("2026-08-11T12:00:01Z"),
        loadedMs = 1_000,
        htmlSizeBytes = 4_096,
    )

    /** Boots a gateway that acks the register then dispatches one fetch job. */
    private fun start(
        jobUrl: String,
        outcome: (String) -> GeckoBrowserManager.FetchOutcome,
    ) {
        server = MockWebServer()
        val dispatcher = QueueDispatcher()
        dispatcher.enqueueResponse(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: okhttp3.Response) {
                    serverSocket = ws
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    if (text.contains("\"register\"")) {
                        ws.send("""{"type":"registered","connectionId":"c-1"}""")
                        ws.send("""{"type":"fetch","jobId":"j-1","url":"$jobUrl"}""")
                    }
                }
            }),
        )
        server.dispatcher = dispatcher
        server.start()

        val app = RuntimeEnvironment.getApplication() as Application
        gateway = GatewayClient(
            appContext = app,
            machineId = "m-1",
            geckoVersion = "152",
            logger = NoopLogger(),
            fetchPage = { url, _, _, _, _, _, _ -> outcome(url) },
            url = server.url("/v1/connect").toString().replace("http://", "ws://"),
            getDeviceToken = { "tok-1" },
            onNavigation = { recorded += it },
        ).also { it.start() }
    }

    /**
     * handleFetch posts to Dispatchers.Main, which under Robolectric is this
     * very thread — so runBlocking here would occupy the looper and the crawl
     * coroutine could never run. Pump the looper instead of blocking on it.
     */
    private fun awaitRecorded(): NavigationResult {
        val deadline = System.currentTimeMillis() + 10_000
        while (recorded.isEmpty() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        check(recorded.isNotEmpty()) { "no navigation was recorded within 10s" }
        return recorded.first()
    }

    @Test
    fun `a successful crawl reaches the feed`() {
        // The regression: this branch recorded nothing, so a worker serving
        // real traffic showed an empty Activity screen.
        start("https://example.com") {
            GeckoBrowserManager.FetchOutcome(nav(true, it), "<html>ok</html>", waitTimedOut = false)
        }
        val row = awaitRecorded()
        assertTrue(row.success)
        assertEquals("https://example.com", row.requestedUrl)
    }

    @Test
    fun `a failed crawl reaches the feed too`() {
        start("https://example.com") {
            GeckoBrowserManager.FetchOutcome(nav(false, it), null, waitTimedOut = false)
        }
        val row = awaitRecorded()
        assertFalse(row.success)
        assertEquals("Navigation timeout after 30000 ms", row.error)
    }

    @Test
    fun `a rejected URL is recorded rather than silently dropped`() {
        // blockPrivateHosts turns this away before the browser is touched; the
        // user should still see that something was attempted and refused.
        start("http://192.168.1.10/admin") {
            throw AssertionError("the browser must not be reached for a private host")
        }
        val row = awaitRecorded()
        assertFalse(row.success)
        assertEquals("http://192.168.1.10/admin", row.requestedUrl)
    }

    @Test
    fun `exactly one record per job`() {
        start("https://example.com") {
            GeckoBrowserManager.FetchOutcome(nav(true, it), "<html>ok</html>", waitTimedOut = false)
        }
        awaitRecorded()
        repeat(20) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(20) }
        assertEquals(1, recorded.size)
    }
}
