package com.meerkly.android.ui

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meerkly.android.MeerklyApp
import com.meerkly.android.model.AuthStatus
import com.meerkly.android.model.EarningsState
import com.meerkly.android.proxy.ProxyState
import com.meerkly.android.worker.WorkerServiceLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class MachineInfo(
    val machineId: String,
    val appVersion: String,
    val deviceModel: String,
    val androidSdk: Int,
    // Literal, not BuildConfig: no field exists for it yet. libs.versions.toml
    // (meerklySdk) is the source of truth this must be kept in sync with.
    val sdkVersion: String,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val graph = (app as MeerklyApp).graph

    // Sign-in + device-link state driving the root UI (gate vs dashboard).
    val authStatus: StateFlow<AuthStatus> = graph.account.status

    /** Live proxy state, so Home can stop claiming "Connected". */
    val proxyState: StateFlow<ProxyState> = graph.proxyController.state

    /** The reason the last start failed, or null. */
    val proxyError: StateFlow<String?> = graph.proxyController.lastError

    /** The account's earnings. Unknown until a fetch succeeds — never render 0. */
    val earnings: StateFlow<EarningsState> = graph.account.earnings

    /** This install's device id, so Devices can mark its own row. */
    val deviceId: String = graph.machineId

    // ---- Background worker control (sticky Stop / Start) -------------------

    private val _workerEnabled = MutableStateFlow(graph.workerPrefs.workerEnabled)
    val workerEnabled: StateFlow<Boolean> = _workerEnabled.asStateFlow()

    /**
     * The user's explicit Stop/Start. Stop persists (nothing auto-restarts —
     * not boot, not app open) until Start is pressed again.
     */
    fun setWorkerEnabled(enabled: Boolean) {
        graph.workerPrefs.workerEnabled = enabled
        _workerEnabled.value = enabled
        val app = getApplication<Application>()
        if (enabled) {
            graph.proxyController.start()
            WorkerServiceLauncher.startIfEligible(app, graph)
            graph.logger.info("worker.started_by_user", mapOf("via" to "dashboard"))
        } else {
            graph.proxyController.stop()
            WorkerServiceLauncher.stop(app)
            graph.logger.info("worker.stopped_by_user", mapOf("via" to "dashboard"))
        }
    }

    /**
     * Re-read everything the dashboard mirrors. Called on every resume: the
     * notification's Stop, the battery exemption and the notification
     * permission can all change while we're backgrounded (or in a system
     * settings screen the checklist sent the user to).
     */
    fun refreshWorkerState() {
        _workerEnabled.value = graph.workerPrefs.workerEnabled
        refreshBatteryExemption()
        refreshNotificationsGranted()
    }

    // ---- Setup checklist state ---------------------------------------------

    private val _batteryExempt = MutableStateFlow(true)
    val batteryExempt: StateFlow<Boolean> = _batteryExempt.asStateFlow()

    private val _notificationsGranted = MutableStateFlow(true)
    val notificationsGranted: StateFlow<Boolean> = _notificationsGranted.asStateFlow()

    /** Recomputed on every dashboard resume — the exemption changes outside the app. */
    fun refreshBatteryExemption() {
        val app = getApplication<Application>()
        val pm = app.getSystemService(PowerManager::class.java)
        _batteryExempt.value = pm?.isIgnoringBatteryOptimizations(app.packageName) ?: true
    }

    /**
     * A notification-permission dialog came back (from either request site).
     * Refreshes the checklist row AND re-posts the worker's ongoing
     * notification, which stays invisible otherwise: on a fresh install the
     * service starts at pairing, before this permission exists, and a grant
     * alone re-posts nothing.
     *
     * Reads the real permission state rather than trusting the dialog's result,
     * so "already granted" and "granted just now" behave identically.
     */
    fun onNotificationsPermissionResult() {
        refreshNotificationsGranted()
        WorkerServiceLauncher.onNotificationPermissionResult(
            getApplication(),
            graph,
            _notificationsGranted.value,
        )
    }

    fun refreshNotificationsGranted() {
        val app = getApplication<Application>()
        _notificationsGranted.value =
            Build.VERSION.SDK_INT < SetupChecklist.NOTIFICATION_PERMISSION_SDK ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** App notification settings — where a permanently-denied step has to go. */
    fun notificationSettingsIntent(): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, getApplication<Application>().packageName)

    /**
     * System dialog asking to exempt Meerkly from battery optimizations (Doze
     * suspends the worker's network otherwise). Some OEMs strip the direct
     * dialog — fall back to the settings list.
     */
    fun batteryExemptionIntent(): Intent {
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${getApplication<Application>().packageName}"),
        )
        return if (direct.resolveActivity(getApplication<Application>().packageManager) != null) {
            direct
        } else {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        }
    }

    /** Refresh earnings (e.g. when the dashboard is shown). */
    fun refreshEarnings() = graph.account.refreshEarnings()

    private val _signingIn = MutableStateFlow(false)
    val signingIn: StateFlow<Boolean> = _signingIn.asStateFlow()

    private val _signInError = MutableStateFlow<String?>(null)
    val signInError: StateFlow<String?> = _signInError.asStateFlow()

    /** The browser intent starting the OAuth flow (launched for result by the gate). */
    fun signInIntent(): Intent = graph.authManager.signInIntent()

    fun onSignInLaunched() {
        _signingIn.value = true
        _signInError.value = null
    }

    fun onSignInFailedToLaunch(message: String) {
        _signingIn.value = false
        _signInError.value = message
    }

    /** Completes the OAuth redirect result: exchange, pair the device, connect the worker. */
    fun onSignInResult(data: Intent?) {
        viewModelScope.launch {
            val error = graph.account.completeSignIn(data)
            _signingIn.value = false
            _signInError.value = error
        }
    }

    fun signOut() {
        viewModelScope.launch { graph.account.signOut() }
    }

    val machineInfo = MachineInfo(
        machineId = graph.machineId,
        appVersion = runCatching {
            app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "?"
        }.getOrDefault("?"),
        deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
        androidSdk = Build.VERSION.SDK_INT,
        sdkVersion = "0.6.0",
    )

    /** Builds the diagnostics ZIP off the main thread and returns it for sharing. */
    suspend fun buildDiagnostics(): File = withContext(Dispatchers.IO) {
        // clientKey is internal to ProxyController (logged, never exposed) —
        // there is nothing meaningful to pass here.
        graph.diagnostics.export(proxyState.value, null)
    }
}
