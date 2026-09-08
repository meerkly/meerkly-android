package com.meerkly.android.worker

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.meerkly.android.AppGraph

/**
 * The single place that decides whether the worker service should run. Every
 * start path funnels through here — MainActivity.onStart, post-pairing,
 * BootReceiver — so the eligibility rule can't drift between them:
 * the user hasn't stopped it, and we know which account to earn for.
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
            publisherId = graph.publisherId,
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
        publisherId = graph.publisherId,
    )

    /**
     * The rule itself, pure so tests can pin every combination.
     *
     * The old rule also required a device token and a compiled-in gateway URL.
     * Neither exists now: the SDK authenticates by publisher id, and an empty
     * gateway list means "the production gateway" rather than "no gateway".
     */
    internal fun eligible(workerEnabled: Boolean, publisherId: String?): Boolean =
        workerEnabled && !publisherId.isNullOrBlank()

    internal fun shouldRepostNotification(
        granted: Boolean,
        workerEnabled: Boolean,
        publisherId: String?,
    ): Boolean = granted && eligible(workerEnabled, publisherId)

    /** User re-enabled or explicitly stopped from in-app UI. */
    fun stop(context: Context) {
        context.applicationContext.stopService(
            Intent(context.applicationContext, WorkerService::class.java),
        )
    }
}
