package com.meerkly.android

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import com.meerkly.android.browser.GeckoBrowserManager
import com.meerkly.android.data.MachineIdManager
import com.meerkly.android.data.RecentNavigationRepository
import com.meerkly.android.diagnostics.DiagnosticsExporter
import com.meerkly.android.gateway.GatewayClient
import com.meerkly.android.logging.AppLogger
import com.meerkly.android.logging.JsonlFileLogger
import com.meerkly.android.logging.LogRetention
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset

/** Application entry point; owns the process-singleton [AppGraph]. */
class MeerklyApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        // GeckoView is multiprocess: its content/GPU/etc. child processes re-instantiate this
        // Application. Only build the app graph (logger, GeckoRuntime, session) in the main
        // process — otherwise every Gecko child would spin up a second runtime and interleave
        // writes to the shared JSONL log.
        if (isMainProcess()) {
            graph = AppGraph(this)
        }
    }

    private fun isMainProcess(): Boolean = currentProcessName() == packageName

    private fun currentProcessName(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return getProcessName()
        val pid = Process.myPid()
        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return am?.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName
    }
}

/**
 * Hand-rolled dependency container built once per process. Keeping the GeckoRuntime/session,
 * logger, and repositories here (not in a ViewModel) means they survive Activity recreation and
 * rotation.
 */
class AppGraph(app: Application) {
    val machineId: String = MachineIdManager.getMachineId(app)
    private val logDir = File(app.filesDir, "logs")
    val logger: AppLogger = JsonlFileLogger(logDir, machineId)
    val recentRepo = RecentNavigationRepository()
    val geckoVersion: String? = runCatching { org.mozilla.geckoview.BuildConfig.MOZILLA_VERSION }.getOrNull()
    val browserManager = GeckoBrowserManager(app, logger)
    val diagnostics = DiagnosticsExporter(app, logger, recentRepo, machineId, geckoVersion)
    val gatewayClient = GatewayClient(app, machineId, geckoVersion, logger, BuildConfig.GATEWAY_URL)

    init {
        runCatching { LogRetention.apply(logDir, LocalDate.now(ZoneOffset.UTC)) }
        logger.info(
            "app.start",
            mapOf("machine_id" to machineId, "sdk" to Build.VERSION.SDK_INT, "geckoview" to geckoVersion),
        )
        browserManager.start()
        // Register this device as a worker with the API gateway.
        gatewayClient.start()
    }
}
