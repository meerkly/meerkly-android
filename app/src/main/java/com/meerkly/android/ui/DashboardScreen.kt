package com.meerkly.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import com.meerkly.android.BuildConfig
import com.meerkly.android.R
import com.meerkly.android.model.AuthStatus
import com.meerkly.android.model.Earnings
import com.meerkly.android.model.EarningsState
import com.meerkly.android.proxy.ProxyState
import com.meerkly.android.util.Formatters
import com.meerkly.android.ui.components.ContentColumn
import com.meerkly.android.ui.components.CheckIcon
import com.meerkly.android.ui.components.ConnectionChip
import com.meerkly.android.ui.components.EarnCard
import com.meerkly.android.ui.components.HeartIcon
import com.meerkly.android.ui.components.IconChip
import com.meerkly.android.ui.components.PhoneIcon
import com.meerkly.android.ui.components.ReassuranceCard
import com.meerkly.android.ui.components.ShieldIcon
import com.meerkly.android.ui.components.StatusChip
import com.meerkly.android.ui.components.TrendIcon
import com.meerkly.android.ui.components.WalletIcon
import com.meerkly.android.ui.nav.WindowWidth
import com.meerkly.android.ui.theme.Display
import com.meerkly.android.ui.theme.Bone
import com.meerkly.android.ui.theme.Cream
import com.meerkly.android.ui.theme.Emerald
import com.meerkly.android.ui.theme.EmeraldDeep
import com.meerkly.android.ui.theme.Gold
import com.meerkly.android.ui.theme.GoldDeep
import com.meerkly.android.ui.theme.InkSoft
import com.meerkly.android.ui.theme.Pink
import com.meerkly.android.ui.theme.PinkDeep
import com.meerkly.android.ui.theme.Rose
import com.meerkly.android.ui.theme.RoseDeep
import com.meerkly.android.ui.theme.RoseSoft
import com.meerkly.android.ui.theme.Sand

/** The friendly signed-in home: hero, earnings, reassurance cards. */
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    auth: AuthStatus.SignedIn,
    width: WindowWidth,
    modifier: Modifier = Modifier,
) {
    // The earnings poll and the ON_RESUME refresh now live in MainScaffold —
    // here they would stop the moment the user left the Home tab.

    // POST_NOTIFICATIONS is requested from the checklist, not on load — an
    // unprompted dialog the moment the dashboard appears reads as a demand,
    // and a refusal there leaves no way back.
    val context = LocalContext.current
    val activity = context as? Activity
    // Two refusals and the system dialog silently no-ops; the row has to send
    // the user to settings instead or the step could never be completed.
    var notificationsPermanentlyDenied by rememberSaveable { mutableStateOf(false) }
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onNotificationsPermissionResult()
        if (!granted && activity != null) {
            notificationsPermanentlyDenied = !ActivityCompat
                .shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val earnings by viewModel.earnings.collectAsState()
    val proxyState by viewModel.proxyState.collectAsState()
    val proxyError by viewModel.proxyError.collectAsState()
    val workerEnabled by viewModel.workerEnabled.collectAsState()
    val batteryExempt by viewModel.batteryExempt.collectAsState()
    val notificationsGranted by viewModel.notificationsGranted.collectAsState()

    // publisherId is null when sign-in succeeded but /api/v1/me could not be
    // read — the account we'd earn for isn't known yet, and the proxy can't
    // start. Stands in for the old "device linked" step.
    val accountReady = auth.publisherId != null

    val setupSteps = SetupChecklist.steps(
        deviceLinked = accountReady,
        notificationsGranted = notificationsGranted,
        notificationsPermanentlyDenied = notificationsPermanentlyDenied,
        batteryExempt = batteryExempt,
        sdkInt = Build.VERSION.SDK_INT,
    )
    // Deliberately NOT defaulted to 0: an unreachable account service must read
    // as "we don't know", not as "you have nothing".
    val loaded = (earnings as? EarningsState.Loaded)?.earnings
    val thisDevice = loaded?.forDevice(viewModel.deviceId)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Bone)
            .verticalScroll(rememberScrollState()),
    ) {
        ContentColumn(maxWidth = width.contentMaxWidthDp.dp) {
            Hero(proxyState, proxyError, workerEnabled)
            if (!accountReady) {
                AccountNotReadyBanner(onRetry = viewModel::refreshEarnings)
            }
            if (loaded == null) {
                EarningsUnavailableBanner()
            }
            // Setup checklist — disappears for good once every step is green.
            if (!SetupChecklist.allDone(setupSteps)) {
                GettingStartedCard(
                    steps = setupSteps,
                    onAction = { action ->
                        when (action) {
                            SetupAction.RequestNotifications ->
                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            SetupAction.OpenNotificationSettings ->
                                runCatching { context.startActivity(viewModel.notificationSettingsIntent()) }
                            SetupAction.RequestBatteryExemption ->
                                runCatching { context.startActivity(viewModel.batteryExemptionIntent()) }
                        }
                    },
                )
            }
            EarnCard(
                label = stringResource(R.string.earn_unpaid_label),
                value = loaded?.let { Formatters.usd(it.unpaidUsd) } ?: Formatters.UNKNOWN_VALUE,
                note = loaded?.let { stringResource(R.string.earn_unpaid_note, Formatters.usd(it.usdPerGb)) }
                    ?: stringResource(R.string.earn_unknown_note),
                chip = { IconChip(listOf(Gold, GoldDeep)) { WalletIcon() } },
            )
            EarnCard(
                label = stringResource(R.string.earn_device_label),
                value = thisDevice?.let { Formatters.usd(it.usd30d) } ?: Formatters.UNKNOWN_VALUE,
                note = loaded?.let { stringResource(R.string.days_window, it.devicesWindowDays) }
                    ?: stringResource(R.string.earn_unknown_note),
                chip = { IconChip(listOf(Pink, PinkDeep)) { PhoneIcon() } },
            )
            EarnCard(
                label = stringResource(R.string.earn_total_label),
                value = loaded?.let { Formatters.usd(it.lifetimeUsd) } ?: Formatters.UNKNOWN_VALUE,
                // lifetimeUsd only counts hours the server has settled, which
                // lags by design — pendingUsd is shown alongside it rather than
                // dropped, or a user who just started earning sees $0.00 next
                // to a device that is visibly working.
                note = loaded?.let { stringResource(R.string.earn_pending_note, Formatters.usd(it.pendingUsd)) }
                    ?: stringResource(R.string.earn_unknown_note),
                chip = { IconChip(listOf(Emerald, EmeraldDeep)) { TrendIcon() } },
            )
            EarningsLinks(loaded)
            // Must track the proxy: "doing its thing" alongside an Offline hero
            // is the same false reassurance the old static chip gave. Hidden
            // entirely while stopped — the worker-control card owns that state.
            if (workerEnabled) {
                ReassuranceCard(
                    title = stringResource(
                        if (proxyState.isEarning) R.string.card_running_title else R.string.card_paused_title,
                    ),
                    note = stringResource(
                        if (proxyState.isEarning) R.string.card_running_note else R.string.card_paused_note,
                    ),
                    chip = {
                        if (proxyState.isEarning) {
                            IconChip(listOf(Emerald, EmeraldDeep)) { CheckIcon() }
                        } else {
                            IconChip(listOf(Gold, GoldDeep)) { CheckIcon() }
                        }
                    },
                )
            }
            ReassuranceCard(
                title = stringResource(R.string.card_safe_title),
                note = stringResource(R.string.card_safe_note),
                chip = { IconChip(listOf(Gold, GoldDeep)) { ShieldIcon() } },
            )
            // Worker control — the deliberate Stop/Start, mirroring the desktop
            // tray's single Quit control.
            WorkerControlCard(
                enabled = workerEnabled,
                onToggle = { viewModel.setWorkerEnabled(!workerEnabled) },
            )
        }
    }
}

@Composable
private fun Hero(proxyState: ProxyState, proxyError: String?, workerEnabled: Boolean) {
    // "You're all set" is only true when the client is actually connected;
    // otherwise say what's wrong instead of reassuring the user falsely. A
    // user-stopped worker is its OWN state — showing Offline copy would read
    // as something being broken when the user chose this.
    val titleRes = when {
        !workerEnabled -> R.string.dash_title_stopped
        proxyState == ProxyState.Connected -> R.string.dash_title
        proxyState == ProxyState.Connecting -> R.string.dash_title_connecting
        else -> R.string.dash_title_offline
    }
    val subRes = when {
        !workerEnabled -> R.string.dash_sub_stopped
        proxyState == ProxyState.Connected -> R.string.dash_sub
        proxyState == ProxyState.Connecting -> R.string.dash_sub_connecting
        else -> R.string.dash_sub_offline
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        MeerklyMascot(modifier = Modifier.size(width = 108.dp, height = 130.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (workerEnabled) {
                ConnectionChip(proxyState)
            } else {
                StatusChip(text = stringResource(R.string.conn_disabled), fg = InkSoft, bg = Sand)
            }
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = Display,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(subRes),
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
            )
            if (workerEnabled && proxyState == ProxyState.Failed && proxyError != null) {
                Text(
                    text = proxyError,
                    style = MaterialTheme.typography.labelSmall,
                    color = RoseDeep,
                )
            }
        }
    }
}

/**
 * Deep links to the web dashboard for the things the app itself doesn't show:
 * the full earnings history, and (when eligible) requesting a payout.
 */
@Composable
private fun EarningsLinks(loaded: Earnings?) {
    val context = LocalContext.current
    fun open(path: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${BuildConfig.ACCOUNT_BASE_URL}$path")))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = { open("/statistics") }) {
            Text(stringResource(R.string.earn_see_link), color = Pink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        if (loaded?.canRequestPayout == true) {
            TextButton(onClick = { open("/statistics") }) {
                Text(
                    stringResource(R.string.earn_payout_link),
                    color = EmeraldDeep,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

/**
 * The deliberate Stop/Start for the background worker — the dashboard twin of
 * the notification's Stop action. Stop is sticky: nothing restarts the worker
 * (boot, app open) until Start is pressed here.
 */
@Composable
private fun WorkerControlCard(enabled: Boolean, onToggle: () -> Unit) {
    Surface(color = Cream, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Sand)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconChip(if (enabled) listOf(Pink, PinkDeep) else listOf(Gold, GoldDeep)) { HeartIcon() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(if (enabled) R.string.worker_card_on_title else R.string.worker_card_off_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(if (enabled) R.string.worker_card_on_note else R.string.worker_card_off_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft,
                )
            }
            OutlinedButton(
                onClick = onToggle,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Sand),
            ) {
                Text(
                    stringResource(if (enabled) R.string.worker_stop else R.string.worker_start),
                    color = if (enabled) RoseDeep else EmeraldDeep,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** Shown when we have no balance to show, so a blank figure never reads as "your earnings are gone". */
@Composable
private fun EarningsUnavailableBanner() {
    Surface(color = Cream, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Sand)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.earnings_unavailable_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.earnings_unavailable_note),
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
            )
        }
    }
}

/** Shown while sign-in succeeded but /api/v1/me hasn't produced a publisher id yet. */
@Composable
private fun AccountNotReadyBanner(onRetry: () -> Unit) {
    Surface(color = RoseSoft, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.account_not_ready_note),
                style = MaterialTheme.typography.bodySmall,
                color = RoseDeep,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = onRetry,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Rose),
            ) {
                Text(
                    stringResource(R.string.account_not_ready_retry),
                    color = RoseDeep,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
