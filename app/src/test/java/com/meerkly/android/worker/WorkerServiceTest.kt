package com.meerkly.android.worker

import android.app.Service
import android.content.Intent
import com.meerkly.android.MeerklyApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkerServiceTest {

    private fun app() = RuntimeEnvironment.getApplication() as MeerklyApp

    @Test
    fun `plain start goes foreground sticky with the ongoing notification`() {
        val controller = Robolectric.buildService(WorkerService::class.java).create()
        val service = controller.get()

        val mode = service.onStartCommand(Intent(app(), WorkerService::class.java), 0, 1)

        assertEquals(Service.START_STICKY, mode)
        val shadow = shadowOf(service)
        assertEquals(WorkerNotification.NOTIFICATION_ID, shadow.lastForegroundNotificationId)
        controller.destroy()
    }

    @Test
    fun `notification Stop is the sticky user stop`() {
        app().graph.workerPrefs.workerEnabled = true
        val controller = Robolectric.buildService(WorkerService::class.java).create()
        val service = controller.get()

        val mode = service.onStartCommand(
            Intent(app(), WorkerService::class.java).setAction(WorkerService.ACTION_STOP),
            0,
            1,
        )

        // Pref persisted false (boot/app-open won't resurrect), self-stopped
        // (STICKY restart defeated), and NOT sticky for this delivery.
        assertFalse(app().graph.workerPrefs.workerEnabled)
        assertTrue(shadowOf(service).isStoppedBySelf)
        assertEquals(Service.START_NOT_STICKY, mode)
        controller.destroy()
    }
}
