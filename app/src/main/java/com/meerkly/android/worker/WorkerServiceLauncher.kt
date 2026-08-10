package com.meerkly.android.worker

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.meerkly.android.AppGraph
import com.meerkly.android.BuildConfig

/**
 * The single place that decides whether the worker service should run. Every
 * start path funnels through here — MainActivity.onStart, post-pairing,
 * BootReceiver — so the eligibility rule can't drift between them:
 * the user hasn't stopped it, the device is paired, and this build has a
 * gateway at all.
 */
object WorkerServiceLauncher {

    fun startIfEligible(context: Context, graph: AppGraph): Boolean {
        if (!isEligible(graph)) return false
        // Foreground-start restrictions: every caller is on an allowed path
        // (visible Activity, BOOT_COMPLETED, or STICKY restart).
        ContextCompat.startForegroundService(
            context.applicationContext,
            Intent(context.applicationContext, WorkerService::class.java),
        )
        return true
    }

    fun isEligible(graph: AppGraph): Boolean = eligible(
        workerEnabled = graph.workerPrefs.workerEnabled,
        deviceToken = graph.deviceRegistration.getDeviceToken(),
        gatewayUrl = BuildConfig.GATEWAY_URL,
    )

    /** The rule itself, pure so tests can pin every combination. */
    internal fun eligible(workerEnabled: Boolean, deviceToken: String?, gatewayUrl: String): Boolean =
        workerEnabled && deviceToken != null && gatewayUrl.isNotBlank()

    /** User re-enabled or explicitly stopped from in-app UI. */
    fun stop(context: Context) {
        context.applicationContext.stopService(
            Intent(context.applicationContext, WorkerService::class.java),
        )
    }
}
