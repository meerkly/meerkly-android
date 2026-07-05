package com.meerkly.android.gateway

import com.meerkly.android.data.DeviceInfo
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GatewayClientFrameTest {

    private val info = DeviceInfo(
        name = "Rasmus's Pixel",
        deviceModel = "Google Pixel 8",
        os = "android 14 (sdk 34)",
        arch = "arm64-v8a",
        appVersion = "1.0",
        engineVersion = "GeckoView 152",
        cpuCores = 8,
        memoryMb = 8192,
        screen = "1080x2400",
        timezone = "Europe/Stockholm",
        locale = "sv-SE",
    )

    @Test
    fun `register frame carries the device token when present`() {
        val frame = JSONObject(GatewayClient.buildRegisterFrame("m-1", info, "tok-123"))

        assertEquals("register", frame.getString("type"))
        assertEquals("m-1", frame.getString("machineId"))
        assertEquals("android", frame.getString("platform"))
        assertEquals("tok-123", frame.getString("deviceToken"))
        val device = frame.getJSONObject("device")
        assertEquals("Google Pixel 8", device.getString("deviceModel"))
        assertEquals("GeckoView 152", device.getString("engineVersion"))
        assertEquals(8, device.getInt("cpuCores"))
        assertEquals(8192, device.getInt("memoryMb"))
        assertEquals("1080x2400", device.getString("screen"))
        assertEquals("Europe/Stockholm", device.getString("timezone"))
        assertEquals("sv-SE", device.getString("locale"))
    }

    @Test
    fun `register frame omits the token field when unpaired`() {
        val frame = JSONObject(GatewayClient.buildRegisterFrame("m-1", info, null))
        assertFalse(frame.has("deviceToken"))

        val blank = JSONObject(GatewayClient.buildRegisterFrame("m-1", info, ""))
        assertFalse(blank.has("deviceToken"))
    }

    @Test
    fun `only device_auth_failed is terminal`() {
        assertTrue(GatewayClient.isTerminalAuthError("device_auth_failed"))
        assertFalse(GatewayClient.isTerminalAuthError("verification_unavailable"))
        assertFalse(GatewayClient.isTerminalAuthError(""))
    }
}
