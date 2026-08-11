package com.meerkly.android.ui

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.meerkly.android.BuildConfig
import com.meerkly.android.R
import com.meerkly.android.ui.components.ContentColumn
import com.meerkly.android.ui.theme.Cream
import com.meerkly.android.ui.theme.EmeraldDeep
import com.meerkly.android.ui.theme.Ink
import com.meerkly.android.ui.theme.InkSoft
import com.meerkly.android.ui.theme.RoseDeep
import com.meerkly.android.ui.theme.Sand
import kotlinx.coroutines.launch

/**
 * Everything the user can change, plus the facts they might need to quote at
 * support.
 *
 * Three things live here because they had nowhere else to live: the permission
 * rows (previously only inside GettingStartedCard, which disappears for good
 * once complete — so revoking notifications later left no way back),
 * diagnostics export (previously debug-builds-only, so you could never ask a
 * real user for a bundle), and the device facts DeviceInfo has always gathered
 * and never shown.
 */
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onShareDiagnostics: (java.io.File) -> Unit,
    onDebugTools: () -> Unit,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    val workerEnabled by viewModel.workerEnabled.collectAsState()
    val batteryExempt by viewModel.batteryExempt.collectAsState()
    val notificationsGranted by viewModel.notificationsGranted.collectAsState()
    val browserVisible by viewModel.browserVisible.collectAsState()

    var notificationsPermanentlyDenied by rememberSaveable { mutableStateOf(false) }
    var exporting by rememberSaveable { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.refreshNotificationsGranted()
        if (!granted && activity != null) {
            notificationsPermanentlyDenied = !ActivityCompat
                .shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ContentColumn {
            Section(stringResource(R.string.settings_section_worker))
            SettingRow(
                title = stringResource(
                    if (workerEnabled) R.string.worker_card_on_title else R.string.worker_card_off_title,
                ),
                note = stringResource(
                    if (workerEnabled) R.string.worker_card_on_note else R.string.worker_card_off_note,
                ),
                actionLabel = stringResource(if (workerEnabled) R.string.worker_stop else R.string.worker_start),
                actionColor = if (workerEnabled) RoseDeep else EmeraldDeep,
                onAction = { viewModel.setWorkerEnabled(!workerEnabled) },
            )
            SettingRow(
                title = stringResource(R.string.settings_watch_title),
                note = stringResource(R.string.settings_watch_note),
                actionLabel = stringResource(
                    if (browserVisible) R.string.browser_hide else R.string.browser_show,
                ),
                onAction = viewModel::toggleBrowserVisible,
            )

            Section(stringResource(R.string.settings_section_permissions))
            SettingRow(
                title = stringResource(R.string.settings_notifications_title),
                note = stringResource(
                    if (notificationsGranted) R.string.settings_notifications_on
                    else R.string.settings_notifications_off,
                ),
                actionLabel = if (notificationsGranted) null else stringResource(R.string.setup_allow),
                onAction = {
                    // Two refusals make the system dialog a no-op; send the user
                    // to settings instead so the row is never a dead end.
                    if (notificationsPermanentlyDenied || Build.VERSION.SDK_INT < 33) {
                        runCatching { context.startActivity(viewModel.notificationSettingsIntent()) }
                    } else {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
            SettingRow(
                title = stringResource(R.string.settings_battery_title),
                note = stringResource(
                    if (batteryExempt) R.string.settings_battery_on else R.string.settings_battery_off,
                ),
                actionLabel = if (batteryExempt) null else stringResource(R.string.setup_allow),
                onAction = { runCatching { context.startActivity(viewModel.batteryExemptionIntent()) } },
            )

            Section(stringResource(R.string.settings_section_help))
            SettingRow(
                title = stringResource(R.string.settings_diagnostics_title),
                note = stringResource(R.string.settings_diagnostics_note),
                actionLabel = stringResource(
                    if (exporting) R.string.settings_diagnostics_busy
                    else R.string.settings_diagnostics_action,
                ),
                enabled = !exporting,
                onAction = {
                    exporting = true
                    scope.launch {
                        // buildDiagnostics is suspend/IO (snapshot + zip); the
                        // Activity owns the share chooser.
                        runCatching { onShareDiagnostics(viewModel.buildDiagnostics()) }
                        exporting = false
                    }
                },
            )

            Section(stringResource(R.string.settings_section_about))
            val info = viewModel.machineInfo
            FactRow(stringResource(R.string.settings_device_id), info.machineId)
            FactRow(stringResource(R.string.settings_app_version), info.appVersion)
            FactRow(stringResource(R.string.settings_model), "${info.deviceModel} · Android ${info.androidSdk}")
            FactRow(
                stringResource(R.string.settings_engine),
                info.geckoViewVersion ?: Formatters_UNKNOWN,
                // Debug tools used to hang off a footer long-press; the footer
                // is gone, so the engine row inherits it.
                onLongPress = { if (BuildConfig.DEBUG) onDebugTools() },
            )

            SettingRow(
                title = stringResource(R.string.dash_sign_out),
                note = stringResource(R.string.settings_sign_out_note),
                actionLabel = stringResource(R.string.dash_sign_out),
                actionColor = RoseDeep,
                onAction = onSignOut,
            )
        }
    }
}

private const val Formatters_UNKNOWN = "—"

@Composable
private fun Section(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = InkSoft,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SettingRow(
    title: String,
    note: String,
    actionLabel: String?,
    actionColor: androidx.compose.ui.graphics.Color = EmeraldDeep,
    enabled: Boolean = true,
    onAction: () -> Unit,
) {
    Surface(color = Cream, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Sand)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Ink)
                Text(note, style = MaterialTheme.typography.bodySmall, color = InkSoft)
            }
            if (actionLabel != null) {
                OutlinedButton(
                    onClick = onAction,
                    enabled = enabled,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Sand),
                ) {
                    Text(actionLabel, color = actionColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FactRow(label: String, value: String, onLongPress: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onLongPress != null) {
                    Modifier.combinedClickable(onClick = {}, onLongClick = onLongPress)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = InkSoft)
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = Ink,
        )
    }
}
