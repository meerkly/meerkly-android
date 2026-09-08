package com.meerkly.android

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import com.meerkly.android.auth.AccountCoordinator
import com.meerkly.android.auth.AuthManager
import com.meerkly.android.data.KeystoreSecureStore
import com.meerkly.android.data.MachineIdManager
import com.meerkly.android.diagnostics.DiagnosticsExporter
import com.meerkly.android.logging.AppLogger
import com.meerkly.android.logging.JsonlFileLogger
import com.meerkly.android.logging.LogRetention
import com.meerkly.android.proxy.ProxyController
import com.meerkly.android.worker.WorkerPrefs
import com.meerkly.android.worker.WorkerServiceLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

class AppGraph(app: Application) {
    val machineId: String = MachineIdManager.getMachineId(app)
    private val logDir = File(app.filesDir, "logs")
    val logger: AppLogger = JsonlFileLogger(logDir, machineId)
    val appVersion: String = runCatching {
        app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "?"
    }.getOrDefault("?")
    val diagnostics = DiagnosticsExporter(app, logger, machineId, appVersion)

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val secureStore = KeystoreSecureStore(app, logger)
    val authManager = AuthManager(
        appContext = app,
        accountBaseUrl = BuildConfig.ACCOUNT_BASE_URL,
        logger = logger,
        store = secureStore,
        // Debug builds talk to the Rails dev server over cleartext; AppAuth's
        // default connection builder would reject the http token endpoint.
        allowInsecureHttp = BuildConfig.DEBUG,
    )

    /** The account we earn for, or null until sign-in has produced one. */
    val publisherId: String? get() = authManager.publisherId

    // internal, not public: ProxyController's own visibility is internal (see
    // ProxyController.kt), and a public property can't expose a narrower type.
    internal val proxyController = ProxyController(
        deviceId = machineId,
        appVersion = appVersion,
        logger = logger,
        // Read on every start, not captured: sign-in may not have happened yet
        // when this graph is built.
        publisherId = { publisherId },
        // Empty means the production gateway. A debug build points at a dev one.
        gatewayAddresses = BuildConfig.GATEWAY_URL.takeIf { it.isNotBlank() }?.let { listOf(it) }.orEmpty(),
    )
    val workerPrefs = WorkerPrefs(app)
    val account = AccountCoordinator(
        authManager, proxyController, logger, scope,
        // Sticky Stop: the coordinator must not resurrect a worker the user
        // turned off (startup heal, sign-in reconnect).
        isWorkerEnabled = { workerPrefs.workerEnabled },
        // Sign-in completed in the foreground — a legal moment to raise the
        // foreground service.
        onWorkerEligible = { WorkerServiceLauncher.startIfEligible(app, this) },
    )

    init {
        runCatching { LogRetention.apply(logDir, LocalDate.now(ZoneOffset.UTC)) }
        logger.info(
            "app.start",
            mapOf("machine_id" to machineId, "sdk" to Build.VERSION.SDK_INT, "app" to appVersion),
        )
        account.onAppStart()
    }
}
