package com.meerkly.android.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.meerkly.android.R
import com.meerkly.android.gateway.WorkerConnection
import com.meerkly.android.model.AuthStatus
import com.meerkly.android.util.Formatters
import com.meerkly.android.ui.components.BrandCard
import com.meerkly.android.ui.components.ContentColumn
import com.meerkly.android.ui.components.CheckIcon
import com.meerkly.android.ui.components.ConnectionChip
import com.meerkly.android.ui.components.EarnCard
import com.meerkly.android.ui.components.HeartIcon
import com.meerkly.android.ui.components.IconChip
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
import com.meerkly.android.ui.theme.Ink
import com.meerkly.android.ui.theme.InkSoft
import com.meerkly.android.ui.theme.Pink
import com.meerkly.android.ui.theme.PinkDeep
import com.meerkly.android.ui.theme.Rose
import com.meerkly.android.ui.theme.RoseDeep
import com.meerkly.android.ui.theme.RoseSoft
import com.meerkly.android.ui.theme.Sand
import kotlinx.coroutines.delay

/** The friendly signed-in home: hero, earnings placeholders, reassurance cards. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    auth: AuthStatus.SignedIn,
    width: WindowWidth,
    modifier: Modifier = Modifier,
) {
    // The credits poll and the ON_RESUME refresh now live in MainScaffold —
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
        viewModel.refreshNotificationsGranted()
        if (!granted && activity != null) {
            notificationsPermanentlyDenied = !ActivityCompat
                .shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val credits by viewModel.credits.collectAsState()
    val connection by viewModel.connection.collectAsState()
    val workerEnabled by viewModel.workerEnabled.collectAsState()
    val batteryExempt by viewModel.batteryExempt.collectAsState()
    val notificationsGranted by viewModel.notificationsGranted.collectAsState()

    val setupSteps = SetupChecklist.steps(
        deviceLinked = auth.deviceLinked,
        notificationsGranted = notificationsGranted,
        notificationsPermanentlyDenied = notificationsPermanentlyDenied,
        batteryExempt = batteryExempt,
        sdkInt = Build.VERSION.SDK_INT,
    )
    // Deliberately NOT defaulted to 0: an unreachable account service must read
    // as "we don't know", not as "you have nothing".
    val known = credits.creditsOrNull
    val myCredits = known?.creditsFor(viewModel.machineInfo.machineId)
    val totalCredits = known?.totalCredits

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Bone)
            .verticalScroll(rememberScrollState()),
    ) {
        ContentColumn(maxWidth = width.contentMaxWidthDp.dp) {
            Hero(connection, workerEnabled)
            auth.deviceLinkError?.let { DeviceLinkErrorBanner(it) }
            if (known == null) {
                CreditsUnavailableBanner()
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
                label = stringResource(R.string.earn_device_label),
                value = myCredits?.let { Formatters.credits(it) } ?: Formatters.UNKNOWN_VALUE,
                note = myCredits?.let { "${Formatters.dollars(it)} · ${stringResource(R.string.earn_device_note)}" }
                    ?: stringResource(R.string.earn_unknown_note),
                chip = { IconChip(listOf(Gold, GoldDeep)) { WalletIcon() } },
            )
            EarnCard(
                label = stringResource(R.string.earn_total_label),
                value = totalCredits?.let { Formatters.credits(it) } ?: Formatters.UNKNOWN_VALUE,
                note = totalCredits?.let { "${Formatters.dollars(it)} · ${stringResource(R.string.earn_total_note)}" }
                    ?: stringResource(R.string.earn_unknown_note),
                chip = { IconChip(listOf(Emerald, EmeraldDeep)) { TrendIcon() } },
            )
            // Must track the socket: "doing its thing" alongside an Offline hero
            // is the same false reassurance the old static chip gave. Hidden
            // entirely while stopped — the worker-control card owns that state.
            if (workerEnabled) {
                ReassuranceCard(
                    title = stringResource(
                        if (connection.isEarning) R.string.card_running_title else R.string.card_paused_title,
                    ),
                    note = stringResource(
                        if (connection.isEarning) R.string.card_running_note else R.string.card_paused_note,
                    ),
                    chip = {
                        if (connection.isEarning) {
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
            // tray's single Quit control (replaces the old "Keep it open" card).
            WorkerControlCard(
                enabled = workerEnabled,
                onToggle = { viewModel.setWorkerEnabled(!workerEnabled) },
            )
        }
    }
}


@Composable
private fun Hero(connection: WorkerConnection, workerEnabled: Boolean) {
    // "You're all set" is only true when the worker is actually in the pool;
    // otherwise say what's wrong instead of reassuring the user falsely. A
    // user-stopped worker is its OWN state — showing Offline copy would read
    // as something being broken when the user chose this.
    val titleRes = when {
        !workerEnabled -> R.string.dash_title_stopped
        connection == WorkerConnection.Connected -> R.string.dash_title
        connection == WorkerConnection.Connecting || connection == WorkerConnection.Registering ->
            R.string.dash_title_connecting
        connection == WorkerConnection.Unpaired -> R.string.dash_title_unpaired
        else -> R.string.dash_title_offline
    }
    val subRes = when {
        !workerEnabled -> R.string.dash_sub_stopped
        connection == WorkerConnection.Connected -> R.string.dash_sub
        connection == WorkerConnection.Connecting || connection == WorkerConnection.Registering ->
            R.string.dash_sub_connecting
        connection == WorkerConnection.Unpaired -> R.string.dash_sub_unpaired
        else -> R.string.dash_sub_offline
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        MeerklyMascot(modifier = Modifier.size(width = 108.dp, height = 130.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (workerEnabled) {
                ConnectionChip(connection)
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

/** Shown when we have no balance to show, so a blank figure never reads as "your credits are gone". */
@Composable
private fun CreditsUnavailableBanner() {
    Surface(color = Cream, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Sand)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.credits_unavailable_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.credits_unavailable_note),
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
            )
        }
    }
}


@Composable
private fun DeviceLinkErrorBanner(message: String) {
    Surface(color = RoseSoft, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = RoseDeep,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(12.dp),
        )
    }
}

// ---- Little stroke icons for the gradient chips --------------------------







