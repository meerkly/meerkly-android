package com.meerkly.android.worker

import android.content.Intent
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Boot receiver gating. Under Robolectric the real MeerklyApp graph exists but
 * the install is unpaired (no device token), so the positive start path is
 * covered by WorkerServiceLauncherTest's pure-rule cases + on-device reboot
 * verification; here we pin the refusals.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BootReceiverTest {

    private val app = RuntimeEnvironment.getApplication()

    @Test
    fun `ignores actions other than BOOT_COMPLETED`() {
        // exported receiver: anyone can send it random intents; only the
        // protected system broadcast may start the worker.
        BootReceiver().onReceive(app, Intent("com.evil.FAKE_BOOT"))
        assertNull(shadowOf(app).peekNextStartedService())
    }

    @Test
    fun `unpaired install does not start the service on boot`() {
        BootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertNull(shadowOf(app).peekNextStartedService())
    }

    @Test
    fun `sticky user stop survives a reboot`() {
        WorkerPrefs(app).workerEnabled = false
        BootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertNull(shadowOf(app).peekNextStartedService())
    }
}
