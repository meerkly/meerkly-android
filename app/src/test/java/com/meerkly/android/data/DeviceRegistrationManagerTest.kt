package com.meerkly.android.data

import com.meerkly.android.logging.AppLogger
import com.meerkly.android.model.LogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** In-memory SecureStore for tests. */
class FakeSecureStore : SecureStore {
    val map = HashMap<String, String>()
    override fun put(key: String, value: String) { map[key] = value }
    override fun get(key: String): String? = map[key]
    override fun remove(key: String) { map.remove(key) }
}

class NoopLogger : AppLogger {
    override fun info(event: String, data: Map<String, Any?>) {}
    override fun warn(event: String, data: Map<String, Any?>) {}
    override fun error(event: String, data: Map<String, Any?>) {}
    override val recentEntries: StateFlow<List<LogEntry>> = MutableStateFlow(emptyList())
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceRegistrationManagerTest {

    private val machineId = "11111111-2222-3333-4444-555555555555"
    private val info = DeviceInfo(
        name = "Rasmus's Pixel",
        deviceModel = "Google Pixel 8",
        os = "android 14 (sdk 34)",
        arch = "arm64-v8a",
        appVersion = "1.0",
        engineVersion = "GeckoView 152",
    )

    private lateinit var server: MockWebServer
    private lateinit var store: FakeSecureStore
    private lateinit var manager: DeviceRegistrationManager

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        store = FakeSecureStore()
        manager = DeviceRegistrationManager(
            accountBaseUrl = server.url("/").toString(),
            machineId = machineId,
            deviceInfo = { info },
            logger = NoopLogger(),
            store = store,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueueToken(token: String, code: Int = 201) {
        server.enqueue(
            MockResponse().setResponseCode(code)
                .setBody("""{"id":1,"machine_id":"$machineId","device_token":"$token"}"""),
        )
    }

    @Test
    fun `first claim stores the token and sends the full device body with bearer auth`() = runTest {
        enqueueToken("tok-1")

        val link = manager.register("access-abc")

        assertTrue(link.deviceLinked)
        assertNull(link.deviceLinkError)
        assertEquals("tok-1", manager.getDeviceToken())

        val recorded = server.takeRequest()
        assertEquals("/api/devices", recorded.path)
        assertEquals("Bearer access-abc", recorded.getHeader("Authorization"))
        val body = JSONObject(recorded.body.readUtf8())
        assertEquals(machineId, body.getString("machine_id"))
        assertEquals("android", body.getString("platform"))
        assertEquals("Rasmus's Pixel", body.getString("name"))
        assertEquals("Google Pixel 8", body.getString("device_model"))
        assertEquals("android 14 (sdk 34)", body.getString("os"))
        assertEquals("arm64-v8a", body.getString("arch"))
        assertEquals("1.0", body.getString("app_version"))
        assertEquals("GeckoView 152", body.getString("engine_version"))
    }

    @Test
    fun `same-user re-claim rotates the stored token`() = runTest {
        enqueueToken("tok-old")
        manager.register("access-abc")
        enqueueToken("tok-new", code = 200)

        manager.register("access-abc")

        assertNotEquals("tok-old", manager.getDeviceToken())
        assertEquals("tok-new", manager.getDeviceToken())
    }

    @Test
    fun `cross-account 409 keeps the device unlinked with the exact message`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(409)
                .setBody("""{"error":"device_already_linked"}"""),
        )

        val link = manager.register("access-abc")

        assertFalse(link.deviceLinked)
        assertEquals(DeviceRegistrationManager.ERROR_ALREADY_LINKED, link.deviceLinkError)
        assertNull(manager.getDeviceToken())
    }

    @Test
    fun `403 stale scope surfaces the sign-out-and-back-in message`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("{}"))

        val link = manager.register("access-abc")

        assertFalse(link.deviceLinked)
        assertEquals(DeviceRegistrationManager.ERROR_STALE_SCOPE, link.deviceLinkError)
    }

    @Test
    fun `network failure is retryable and never clears a stored token`() = runTest {
        enqueueToken("tok-1")
        manager.register("access-abc")

        server.shutdown() // registration target gone

        val link = manager.register("access-abc")

        // Still linked: the stored token survives a failed refresh attempt.
        assertTrue(link.deviceLinked)
        assertEquals("tok-1", manager.getDeviceToken())
    }

    @Test
    fun `stored token for a different machineId is ignored`() = runTest {
        enqueueToken("tok-1")
        manager.register("access-abc")
        assertNotNull(manager.getDeviceToken())

        val other = DeviceRegistrationManager(
            accountBaseUrl = server.url("/").toString(),
            machineId = "99999999-0000-0000-0000-000000000000",
            deviceInfo = { info },
            logger = NoopLogger(),
            store = store, // same storage, new machine id (restored backup case)
        )
        assertNull(other.getDeviceToken())
    }
}
