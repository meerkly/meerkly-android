package com.meerkly.android.auth

import com.meerkly.android.model.Earnings
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The JSON contracts the app depends on. Parsing is tested against a real
 * socket rather than a hand-built string so a change in either shape fails
 * here rather than on a user's phone.
 */
class AuthManagerApiTest {

    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun body(json: String) = MockResponse().setResponseCode(200).setBody(json)

    @Test
    fun `me returns the publisher id`() {
        server.enqueue(body("""{"id":1,"email":"a@b.com","publisher_id":"pub_abc123"}"""))

        val json = JSONObject(fetch("/api/v1/me"))

        assertEquals("pub_abc123", AuthManager.parsePublisherId(json))
        assertEquals("a@b.com", AuthManager.parseEmail(json))
    }

    @Test
    fun `a me response with no publisher id yields null, not an empty string`() {
        val json = JSONObject("""{"id":1,"email":"a@b.com"}""")

        assertNull(AuthManager.parsePublisherId(json))
    }

    @Test
    fun `earnings parses the full contract`() {
        val json = JSONObject(
            """
            {"unpaid_usd":1.23,"lifetime_usd":4.56,"pending_usd":0.11,
             "bytes_shared":123456789,"settled_bytes":120000000,
             "usd_per_gb":0.35,"minimum_payout_usd":5.0,
             "can_request_payout":false,"devices_window_days":30,
             "devices":[{"device_id":"dev_a","label":"Pixel 8","online":true,
                         "bytes_30d":1000,"usd_30d":0.42,"pending":true}]}
            """.trimIndent(),
        )

        val earnings = AuthManager.parseEarnings(json)

        assertEquals(1.23, earnings.unpaidUsd, 0.0001)
        assertEquals(4.56, earnings.lifetimeUsd, 0.0001)
        assertEquals(0.11, earnings.pendingUsd, 0.0001)
        assertEquals(30, earnings.devicesWindowDays)
        assertEquals(123456789L, earnings.bytesShared)
        assertEquals(0.35, earnings.usdPerGb, 0.0001)
        assertEquals(5.0, earnings.minimumPayoutUsd, 0.0001)
        assertEquals(false, earnings.canRequestPayout)

        val device = earnings.forDevice("dev_a")!!
        assertEquals("Pixel 8", device.label)
        assertTrue(device.online)
        assertEquals(1000L, device.bytes30d)
        assertEquals(0.42, device.usd30d, 0.0001)
        assertTrue(device.pending)
    }

    @Test
    fun `earnings with no devices parses to an empty list, not a crash`() {
        val json = JSONObject("""{"unpaid_usd":0.0,"lifetime_usd":0.0,"bytes_shared":0}""")

        val earnings = AuthManager.parseEarnings(json)

        assertEquals(emptyList<Any>(), earnings.devices)
        assertNull(earnings.forDevice("dev_missing"))
    }

    private fun fetch(path: String): String =
        okhttp3.OkHttpClient().newCall(
            okhttp3.Request.Builder().url(server.url(path)).build(),
        ).execute().use { it.body!!.string() }
}
