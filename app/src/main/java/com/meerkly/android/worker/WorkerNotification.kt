package com.meerkly.android.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import com.meerkly.android.MainActivity
import com.meerkly.android.R
import com.meerkly.android.gateway.WorkerConnection

/**
 * The worker's ongoing notification — the Android analogue of the desktop
 * tray: a quiet persistent surface whose text tracks the live socket state and
 * whose single action is the deliberate Stop.
 */
object WorkerNotification {

    const val CHANNEL_ID = "worker"
    const val NOTIFICATION_ID = 1

    /**
     * Pure state -> text mapping, split out so a test can pin every
     * [WorkerConnection] to honest copy (the notification must never claim
     * more than the dashboard would).
     */
    @StringRes
    fun textFor(connection: WorkerConnection): Int = when (connection) {
        WorkerConnection.Connected -> R.string.notif_text_connected
        WorkerConnection.Connecting, WorkerConnection.Registering -> R.string.notif_text_connecting
        WorkerConnection.Offline -> R.string.notif_text_offline
        WorkerConnection.Unpaired -> R.string.notif_text_unpaired
        // Disabled/Disconnected: the launcher never starts the service for a
        // gateway-less build, and Disconnected is a transient stop state — if
        // either is ever visible, "connecting" is the least-wrong copy.
        WorkerConnection.Disabled, WorkerConnection.Disconnected -> R.string.notif_text_connecting
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
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

    fun build(context: Context, connection: WorkerConnection): Notification {
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
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_meerkly)
            .setContentTitle(context.getString(R.string.notif_title))
            .setContentText(context.getString(textFor(connection)))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, context.getString(R.string.notif_action_stop), stop)
            .build()
    }
}
