package com.meerkly.android.worker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The single eligibility rule every start path (Activity, pairing, boot) shares. */
class WorkerServiceLauncherTest {

    @Test
    fun `eligible only when enabled, paired, and a gateway exists`() {
        assertTrue(WorkerServiceLauncher.eligible(true, "tok", "wss://gw/v1/connect"))
    }

    @Test
    fun `a sticky user stop wins over everything`() {
        assertFalse(WorkerServiceLauncher.eligible(false, "tok", "wss://gw/v1/connect"))
    }

    @Test
    fun `unpaired devices never raise the service`() {
        assertFalse(WorkerServiceLauncher.eligible(true, null, "wss://gw/v1/connect"))
    }

    @Test
    fun `a build with no gateway never raises the service`() {
        // Matches WorkerConnection.Disabled semantics: no notification for a
        // build that can never connect.
        assertFalse(WorkerServiceLauncher.eligible(true, "tok", ""))
    }

    // --- re-posting the ongoing notification after a late permission grant ---
    //
    // On a fresh install the service starts at pairing, BEFORE the checklist
    // asks for POST_NOTIFICATIONS. Android 13+ keeps such a service running but
    // hides its notification from the drawer, and nothing in the app re-posted
    // it once the permission arrived — so the worker ran invisibly until the
    // user toggled background mode off and on again.

    @Test
    fun `a late notification grant re-posts the ongoing notification`() {
        assertTrue(
            WorkerServiceLauncher.shouldRepostNotification(
                granted = true,
                workerEnabled = true,
                deviceToken = "tok",
                gatewayUrl = "wss://gw/v1/connect",
            ),
        )
    }

    @Test
    fun `a denied notification permission posts nothing`() {
        assertFalse(
            WorkerServiceLauncher.shouldRepostNotification(
                granted = false,
                workerEnabled = true,
                deviceToken = "tok",
                gatewayUrl = "wss://gw/v1/connect",
            ),
        )
    }

    @Test
    fun `a notification grant never resurrects a worker the user stopped`() {
        assertFalse(
            WorkerServiceLauncher.shouldRepostNotification(
                granted = true,
                workerEnabled = false,
                deviceToken = "tok",
                gatewayUrl = "wss://gw/v1/connect",
            ),
        )
    }

    @Test
    fun `a notification grant on an unpaired device posts nothing`() {
        assertFalse(
            WorkerServiceLauncher.shouldRepostNotification(
                granted = true,
                workerEnabled = true,
                deviceToken = null,
                gatewayUrl = "wss://gw/v1/connect",
            ),
        )
    }
}
