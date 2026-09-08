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
        start(context)
        return true
    }

    /**
     * A notification-permission result came back. Re-issues the foreground start
     * when the worker should be running, because the service may already be up
     * with its notification suppressed — see [shouldRepostNotification].
     *
     * Both request sites must call this: the Settings row and the Home
     * getting-started checklist. Returns true when the start was re-issued.
     */
    fun onNotificationPermissionResult(context: Context, graph: AppGraph, granted: Boolean): Boolean {
        val repost = shouldRepostNotification(
            granted = granted,
            workerEnabled = graph.workerPrefs.workerEnabled,
            deviceToken = graph.deviceRegistration.getDeviceToken(),
            gatewayUrl = BuildConfig.GATEWAY_URL,
        )
        if (!repost) return false
        // startForegroundService on an ALREADY-running service still runs
        // onStartCommand, and so startForeground, which re-posts the
        // notification — now that the permission lets it be seen.
        start(context)
        return true
    }

    // Foreground-start restrictions: every caller is on an allowed path
    // (visible Activity, BOOT_COMPLETED, or STICKY restart).
    private fun start(context: Context) {
        ContextCompat.startForegroundService(
            context.applicationContext,
            Intent(context.applicationContext, WorkerService::class.java),
        )
    }

    fun isEligible(graph: AppGraph): Boolean = eligible(
        workerEnabled = graph.workerPrefs.workerEnabled,
        deviceToken = graph.deviceRegistration.getDeviceToken(),
        gatewayUrl = BuildConfig.GATEWAY_URL,
    )

    /** The rule itself, pure so tests can pin every combination. */
    internal fun eligible(workerEnabled: Boolean, deviceToken: String?, gatewayUrl: String): Boolean =
        workerEnabled && deviceToken != null && gatewayUrl.isNotBlank()

    /**
     * Should a notification-permission result re-post the ongoing notification?
     *
     * On a fresh install the service starts at pairing, before the checklist
     * asks for POST_NOTIFICATIONS. Android 13+ runs such a service but keeps its
     * notification out of the drawer (it shows only in Task Manager), and the
     * only things that post it are [WorkerService]'s startForeground and a
     * change in the gateway connection state — neither of which a permission
     * grant triggers. The worker then runs invisibly until something restarts
     * the service.
     */
    internal fun shouldRepostNotification(
        granted: Boolean,
        workerEnabled: Boolean,
        deviceToken: String?,
        gatewayUrl: String,
    ): Boolean = granted && eligible(workerEnabled, deviceToken, gatewayUrl)

    /** User re-enabled or explicitly stopped from in-app UI. */
    fun stop(context: Context) {
        context.applicationContext.stopService(
            Intent(context.applicationContext, WorkerService::class.java),
        )
    }
}
