package com.meerkly.android.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.meerkly.android.MainActivity
import com.meerkly.android.R
import com.meerkly.android.proxy.ProxyState

/**
 * The worker's ongoing notification — the Android analogue of the desktop
 * tray: a quiet persistent surface whose text tracks the live socket state and
 * whose single action is the deliberate Stop.
 */
object WorkerNotification {

    const val CHANNEL_ID = "worker"
    const val NOTIFICATION_ID = 1

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_name),
                // LOW: visible in the shade, no sound, no heads-up peek.
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    fun build(context: Context, state: ProxyState): Notification {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            context,
            1,
            Intent(context, WorkerService::class.java).setAction(WorkerService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = when (state) {
            ProxyState.Connected -> context.getString(R.string.worker_notification_earning)
            ProxyState.Connecting -> context.getString(R.string.worker_notification_connecting)
            ProxyState.Failed -> context.getString(R.string.worker_notification_problem)
            ProxyState.Disconnected, ProxyState.Stopped ->
                context.getString(R.string.worker_notification_paused)
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_meerkly)
            .setContentTitle(context.getString(R.string.notif_title))
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, context.getString(R.string.notif_action_stop), stop)
            .build()
    }
}
