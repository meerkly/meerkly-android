package com.meerkly.android.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.meerkly.android.MeerklyApp

/**
 * Restarts the worker after a reboot, without the user opening the app.
 *
 * BOOT_COMPLETED is an explicit exemption from background-FGS-start
 * restrictions, and `specialUse` is on the boot-launchable list (Android 15+).
 * Not directBootAware, so this fires after first unlock — which is required
 * anyway: the device token lives behind the AndroidKeyStore.
 *
 * Respects the sticky Stop: [WorkerServiceLauncher.startIfEligible] checks
 * workerEnabled, so a user who stopped the worker stays stopped across
 * reboots.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as? MeerklyApp ?: return
        val started = WorkerServiceLauncher.startIfEligible(context, app.graph)
        app.graph.logger.info("worker.boot", mapOf("started" to started))
    }
}
