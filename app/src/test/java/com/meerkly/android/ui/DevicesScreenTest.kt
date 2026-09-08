package com.meerkly.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.meerkly.android.model.DeviceEarnings
import com.meerkly.android.ui.nav.WindowWidth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Devices takes plain state and no ViewModel, so it renders on the JVM. That
 * constraint is the whole reason this test can exist — a ViewModel would drag
 * in AppGraph and a live ProxyClient.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DevicesScreenTest {

    @get:Rule val compose = createComposeRule()

    // DeviceEarnings' windowed fields are named bytes30d/usd30d (Task 8's
    // model, matching /api/v1/earnings' bytes_30d/usd_30d) — not bytes/usd.
    private val phone = DeviceEarnings(
        deviceId = "dev_phone", label = "Kitchen phone", online = true,
        bytes30d = 2_000_000_000, usd30d = 0.20, pending = false,
    )
    private val server = DeviceEarnings(
        deviceId = "dev_server", label = "basement-pi", online = false,
        bytes30d = 500_000_000, usd30d = 0.05, pending = true,
    )

    @Test
    fun `every device is listed by its label`() {
        compose.setContent {
            DevicesContent(listOf(phone, server), thisDeviceId = "dev_phone", width = WindowWidth.Compact)
        }

        compose.onNodeWithText("Kitchen phone").assertIsDisplayed()
        compose.onNodeWithText("basement-pi").assertIsDisplayed()
    }

    @Test
    fun `this install is marked so the user can find it`() {
        compose.setContent {
            DevicesContent(listOf(phone, server), thisDeviceId = "dev_phone", width = WindowWidth.Compact)
        }

        compose.onNodeWithText("This device").assertIsDisplayed()
    }

    @Test
    fun `an empty list renders the empty state rather than a bare screen`() {
        compose.setContent {
            DevicesContent(emptyList(), thisDeviceId = "dev_phone", width = WindowWidth.Compact)
        }

        compose.onNodeWithText("No devices yet").assertIsDisplayed()
    }
}
