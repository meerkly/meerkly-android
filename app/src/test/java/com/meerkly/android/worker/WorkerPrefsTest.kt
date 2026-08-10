package com.meerkly.android.worker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkerPrefsTest {

    private fun prefs() = WorkerPrefs(RuntimeEnvironment.getApplication())

    @Test
    fun `worker is enabled by default - always run while paired`() {
        assertTrue(prefs().workerEnabled)
    }

    @Test
    fun `an explicit stop is sticky across instances`() {
        prefs().workerEnabled = false
        // A fresh instance (new process, boot receiver, STICKY restart) must
        // still see the user's stop.
        assertFalse(prefs().workerEnabled)
        prefs().workerEnabled = true
        assertTrue(prefs().workerEnabled)
    }
}
