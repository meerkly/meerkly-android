package com.meerkly.android.worker

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.meerkly.android.MeerklyApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the worker process alive while the app is
 * backgrounded or the screen is locked — the Android analogue of the desktop
 * app's hide-on-close + tray.
 *
 * Deliberately THIN: [com.meerkly.android.AppGraph] (Application-scoped) owns
 * the GatewayClient/GeckoBrowserManager; this service owns only two things —
 * the startForeground posture ("don't kill this process") and the ongoing
 * notification. It reads the graph off the Application, which is safe because
 * Application.onCreate always completes before any Service callback in the
 * process, and services run in the main process where the graph is built.
 *
 * FGS type is `specialUse` (long-lived workload fitting no other bucket):
 * dataSync is capped at 6h/day on Android 15+ and can't launch from boot.
 *
 * Lifecycle contract:
 *  - START_STICKY: the OS restarts us after process death; Application re-init
 *    reconnects the gateway first, we just re-post the notification.
 *  - [ACTION_STOP] (notification action) is the user's explicit Stop: persist
 *    workerEnabled=false, stop the gateway, stopSelf — sticky restart defeated,
 *    and every auto-start path (Activity, boot, pairing) checks the pref.
 *  - onTaskRemoved: intentionally nothing — swiping the app away must not stop
 *    earning.
 *  - onDestroy never touches the gateway: the process legitimately outlives
 *    the service (e.g. user opens the app right after a Stop).
 */
class WorkerService : Service() {

    private var scope: CoroutineScope? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val graph = (application as MeerklyApp).graph

        if (intent?.action == ACTION_STOP) {
            graph.logger.info("worker.stopped_by_user", mapOf("via" to "notification"))
            graph.workerPrefs.workerEnabled = false
            graph.gatewayClient.stop()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        WorkerNotification.ensureChannel(this)
        ServiceCompat.startForeground(
            this,
            WorkerNotification.NOTIFICATION_ID,
            WorkerNotification.build(this, graph.gatewayClient.connection.value),
            // SPECIAL_USE exists (and typing is mandatory) from API 34; below
            // that an untyped foreground start is the correct call.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
        graph.logger.info("worker.service_started")

        // Defensive: a STICKY restart may race coordinator gating; start() is a
        // no-op when already connected/connecting.
        graph.gatewayClient.start()

        // Live notification text. Scope is created once even if
        // onStartCommand runs again (redelivered intents, re-launches).
        if (scope == null) {
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { s ->
                s.launch {
                    graph.gatewayClient.connection.collect { conn ->
                        // POST_NOTIFICATIONS may be denied (33+): skip the update
                        // rather than throw — the FGS itself is unaffected.
                        val manager = NotificationManagerCompat.from(this@WorkerService)
                        if (Build.VERSION.SDK_INT < 33 ||
                            ContextCompat.checkSelfPermission(this@WorkerService, Manifest.permission.POST_NOTIFICATIONS) ==
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            manager.notify(WorkerNotification.NOTIFICATION_ID, WorkerNotification.build(this@WorkerService, conn))
                        }
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.meerkly.android.worker.STOP"
    }
}
