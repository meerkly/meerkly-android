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
}
