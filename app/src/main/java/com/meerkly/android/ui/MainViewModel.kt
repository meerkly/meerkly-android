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
import com.meerkly.android.model.BrowserStatus
import com.meerkly.android.gateway.WorkerConnection
import com.meerkly.android.model.CreditsState
import com.meerkly.android.model.NavigationResult
import com.meerkly.android.util.UrlValidator
import com.meerkly.android.worker.WorkerServiceLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

data class MachineInfo(
    val machineId: String,
    val appVersion: String,
    val deviceModel: String,
    val androidSdk: Int,
    val geckoViewVersion: String?,
    val profileStatus: String,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val graph = (app as MeerklyApp).graph

    val browserManager = graph.browserManager
    val logs = graph.logger.recentEntries
    val recent = graph.recentRepo.recent

    private val _status = MutableStateFlow<BrowserStatus>(BrowserStatus.Idle)
    val status: StateFlow<BrowserStatus> = _status.asStateFlow()

    // Sign-in + device-link state driving the root UI (gate vs dashboard).
    val authStatus: StateFlow<AuthStatus> = graph.account.status

    // The signed-in user's earnings for the dashboard cards. Unknown until a
    // fetch succeeds — the UI must render that as "unknown", never as 0.
    val credits: StateFlow<CreditsState> = graph.account.credits

    /** Live worker socket state, so the dashboard can stop claiming "Connected". */
    val connection: StateFlow<WorkerConnection> = graph.gatewayClient.connection

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
            graph.gatewayClient.start()
            WorkerServiceLauncher.startIfEligible(app, graph)
            graph.logger.info("worker.started_by_user", mapOf("via" to "dashboard"))
        } else {
            graph.gatewayClient.stop()
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
    fun refreshCredits() = graph.account.refreshCredits()

    /**
     * Debug view: whether the automation browser is on screen. Mirrors the
     * desktop's toggle-browser-window — the worker's GeckoView is always
     * composed and active, this only decides whether it's expanded into a
     * visible panel or collapsed to the offscreen 1dp surface extraction needs.
     * Lives here (not in the manager) so it survives rotation with the rest of
     * the UI state; the engine itself is unaffected either way.
     */
    private val _browserVisible = MutableStateFlow(false)
    val browserVisible: StateFlow<Boolean> = _browserVisible.asStateFlow()

    /** Show/hide the automation browser. Returns nothing — observe [browserVisible]. */
    fun toggleBrowserVisible() {
        _browserVisible.value = !_browserVisible.value
    }

    fun hideBrowser() {
        _browserVisible.value = false
    }

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
        geckoViewVersion = graph.geckoVersion,
        profileStatus = "app-scoped default profile",
    )

    fun onOpen(input: String) {
        UrlValidator.validateAndNormalize(input).fold(
            onSuccess = { url ->
                _status.value = BrowserStatus.Loading(url)
                viewModelScope.launch {
                    val nav = graph.browserManager.navigate(url)
                    graph.recentRepo.record(nav)
                    graph.logger.info("browser.navigation_completed", nav.toJsonMap())
                    _status.value = if (nav.success) BrowserStatus.Success(nav) else BrowserStatus.Error(nav)
                }
            },
            onFailure = { e ->
                val now = Instant.now()
                graph.logger.warn("url.rejected", mapOf("input" to input, "error" to e.message))
                _status.value = BrowserStatus.Error(
                    NavigationResult(
                        success = false,
                        requestedUrl = input,
                        finalUrl = null,
                        title = null,
                        error = e.message ?: "Invalid URL",
                        startedAt = now,
                        finishedAt = now,
                        loadedMs = null,
                        htmlSizeBytes = null,
                    )
                )
            },
        )
    }

    fun onStop() = graph.browserManager.stopLoading()

    fun onReload() = graph.browserManager.reload()

    /** Builds the diagnostics ZIP off the main thread and returns it for sharing. */
    suspend fun buildDiagnostics(): File = withContext(Dispatchers.IO) {
        graph.recentRepo.persist(File(getApplication<Application>().filesDir, "recent_navigations.json"))
        graph.diagnostics.export(_status.value)
    }
}
