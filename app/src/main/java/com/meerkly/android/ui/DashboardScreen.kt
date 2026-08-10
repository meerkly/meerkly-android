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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.meerkly.android.R
import com.meerkly.android.gateway.WorkerConnection
import com.meerkly.android.model.AuthStatus
import com.meerkly.android.model.Credits
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
    onToggleBrowser: () -> Unit,
    browserVisible: Boolean,
    onFooterLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Earnings: refresh on entry, then poll while the dashboard is shown so the
    // balance climbs as crawls are served. Until loaded the cards read 0 credits.
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refreshCredits()
            delay(CREDITS_POLL_MS)
        }
    }
    val credits by viewModel.credits.collectAsState()
    val connection by viewModel.connection.collectAsState()
    // Deliberately NOT defaulted to 0: an unreachable account service must read
    // as "we don't know", not as "you have nothing".
    val known = credits.creditsOrNull
    val myCredits = known?.creditsFor(viewModel.machineInfo.machineId)
    val totalCredits = known?.totalCredits

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Bone),
    ) {
        TopBar(viewModel, auth)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Hero(connection)
            auth.deviceLinkError?.let { DeviceLinkErrorBanner(it) }
            if (known == null) {
                CreditsUnavailableBanner()
            }
            EarnCard(
                label = stringResource(R.string.earn_device_label),
                value = myCredits?.let { formatCredits(it) } ?: UNKNOWN_VALUE,
                note = myCredits?.let { "${formatDollars(it)} · ${stringResource(R.string.earn_device_note)}" }
                    ?: stringResource(R.string.earn_unknown_note),
                chip = { IconChip(listOf(Gold, GoldDeep)) { WalletIcon() } },
            )
            EarnCard(
                label = stringResource(R.string.earn_total_label),
                value = totalCredits?.let { formatCredits(it) } ?: UNKNOWN_VALUE,
                note = totalCredits?.let { "${formatDollars(it)} · ${stringResource(R.string.earn_total_note)}" }
                    ?: stringResource(R.string.earn_unknown_note),
                chip = { IconChip(listOf(Emerald, EmeraldDeep)) { TrendIcon() } },
            )
            // Must track the socket: "doing its thing" alongside an Offline hero
            // is the same false reassurance the old static chip gave.
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
            ReassuranceCard(
                title = stringResource(R.string.card_safe_title),
                note = stringResource(R.string.card_safe_note),
                chip = { IconChip(listOf(Gold, GoldDeep)) { ShieldIcon() } },
            )
            ReassuranceCard(
                title = stringResource(R.string.card_open_title),
                note = stringResource(R.string.card_open_note),
                chip = { IconChip(listOf(Pink, PinkDeep)) { HeartIcon() } },
            )
        }
        Footer(
            viewModel,
            onToggleBrowser = onToggleBrowser,
            browserVisible = browserVisible,
            onLongPress = onFooterLongPress,
        )
    }
}

// How often the dashboard re-fetches the balance while it's on screen, so it
// climbs as crawls are served (credits land a few seconds after each crawl).
private const val CREDITS_POLL_MS = 30_000L

// Stands in for a balance we don't have. An em dash, never "0" — see CreditsState.
private const val UNKNOWN_VALUE = "—"

// "12,300 credits" — the earn cards show credits, not dollars.
private fun formatCredits(credits: Long): String = "%,d credits".format(credits)

// "≈ $0.02" — the dollar hint under a credit balance (500,000 credits = $1).
private fun formatDollars(credits: Long): String =
    "≈ $%.2f".format(credits / Credits.CREDITS_PER_DOLLAR)

@Composable
private fun TopBar(viewModel: MainViewModel, auth: AuthStatus.SignedIn) {
    Surface(color = Cream.copy(alpha = 0.9f)) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BrandCoin(modifier = Modifier.size(24.dp))
                Text(
                    text = "meerkly",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                Spacer(Modifier.weight(1f))
                if (auth.deviceLinked) {
                    StatusChip(text = stringResource(R.string.device_linked), fg = EmeraldDeep, bg = Emerald.copy(alpha = 0.1f))
                }
                OutlinedButton(
                    onClick = { viewModel.signOut() },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Sand),
                ) {
                    Text(stringResource(R.string.dash_sign_out), color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                text = auth.email,
                style = MaterialTheme.typography.labelSmall,
                color = InkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun Hero(connection: WorkerConnection) {
    // "You're all set" is only true when the worker is actually in the pool;
    // otherwise say what's wrong instead of reassuring the user falsely.
    val titleRes = when (connection) {
        WorkerConnection.Connected -> R.string.dash_title
        WorkerConnection.Connecting, WorkerConnection.Registering -> R.string.dash_title_connecting
        WorkerConnection.Unpaired -> R.string.dash_title_unpaired
        else -> R.string.dash_title_offline
    }
    val subRes = when (connection) {
        WorkerConnection.Connected -> R.string.dash_sub
        WorkerConnection.Connecting, WorkerConnection.Registering -> R.string.dash_sub_connecting
        WorkerConnection.Unpaired -> R.string.dash_sub_unpaired
        else -> R.string.dash_sub_offline
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        MeerklyMascot(modifier = Modifier.size(width = 108.dp, height = 130.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ConnectionChip(connection)
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Serif,
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
 * The worker's real socket state. This used to be a static "Connected" label,
 * which told people they were earning while the gateway was unreachable — the
 * dot only pulses when the worker is genuinely in the dispatch pool.
 */
@Composable
private fun ConnectionChip(connection: WorkerConnection) {
    val pulse by rememberInfiniteTransition(label = "live")
        .animateFloat(
            initialValue = 0.4f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse),
            label = "liveAlpha",
        )
    val (labelRes, dot) = when (connection) {
        WorkerConnection.Connected -> R.string.conn_connected to Emerald
        WorkerConnection.Connecting, WorkerConnection.Registering -> R.string.conn_connecting to Gold
        WorkerConnection.Offline -> R.string.conn_offline to Rose
        WorkerConnection.Unpaired -> R.string.conn_unpaired to Rose
        WorkerConnection.Disconnected -> R.string.conn_offline to Rose
        WorkerConnection.Disabled -> R.string.conn_disabled to InkSoft
    }
    Surface(color = Cream, shape = RoundedCornerShape(999.dp), border = BorderStroke(1.dp, Sand)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    // Only the earning state animates; a steady dot reads as
                    // "stopped" at a glance.
                    .alpha(if (connection.isEarning) pulse else 1f)
                    .background(dot, CircleShape),
            )
            Text(
                stringResource(labelRes),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
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
private fun StatusChip(text: String, fg: Color, bg: Color) {
    Surface(color = bg, shape = RoundedCornerShape(999.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
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

@Composable
private fun BrandCard(content: @Composable () -> Unit) {
    Surface(
        color = Cream,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Sand),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) { content() }
}

@Composable
private fun EarnCard(label: String, value: String, note: String, chip: @Composable () -> Unit) {
    BrandCard {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            chip()
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = InkSoft,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                text = value,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                color = Ink,
            )
            Text(text = note, style = MaterialTheme.typography.bodySmall, color = InkSoft)
        }
    }
}

@Composable
private fun ReassuranceCard(title: String, note: String, chip: @Composable () -> Unit) {
    BrandCard {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            chip()
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                Text(note, style = MaterialTheme.typography.bodySmall, color = InkSoft)
            }
        }
    }
}

@Composable
private fun Footer(
    viewModel: MainViewModel,
    onToggleBrowser: () -> Unit,
    browserVisible: Boolean,
    onLongPress: () -> Unit,
) {
    val info = viewModel.machineInfo
    Surface(color = Bone) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = {}, onLongClick = onLongPress)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Device ${info.machineId.take(8)}…",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = InkSoft,
                )
                Text(
                    text = "v${info.appVersion} · Android ${info.androidSdk}",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSoft,
                )
            }
            // Debug view: watch gateway crawls happen in the automation browser.
            TextButton(
                onClick = onToggleBrowser,
                modifier = Modifier.align(Alignment.Start),
            ) {
                Text(
                    text = stringResource(
                        if (browserVisible) R.string.browser_hide else R.string.browser_show,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSoft,
                )
            }
        }
    }
}

// ---- Little stroke icons for the gradient chips --------------------------

@Composable
private fun IconChip(gradient: List<Color>, icon: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .background(Brush.linearGradient(gradient), RoundedCornerShape(13.dp)),
        contentAlignment = Alignment.Center,
    ) { icon() }
}

@Composable
private fun StrokeIcon(builder: Path.() -> Unit) {
    Canvas(modifier = Modifier.size(22.dp)) {
        // Paths are authored in a 24x24 box.
        scale(size.width / 24f, pivot = Offset.Zero) {
            drawPath(
                Path().apply(builder),
                Color.White,
                style = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

@Composable
private fun CheckIcon() = StrokeIcon {
    moveTo(20f, 6f); lineTo(9f, 17f); lineTo(4f, 12f)
}

@Composable
private fun ShieldIcon() = StrokeIcon {
    moveTo(12f, 3f); lineTo(19f, 6f); lineTo(19f, 11f)
    cubicTo(19f, 15.4f, 16f, 18.4f, 12f, 19.5f)
    cubicTo(8f, 18.4f, 5f, 15.4f, 5f, 11f)
    lineTo(5f, 6f); close()
    moveTo(9.5f, 12f); lineTo(11.3f, 13.8f); lineTo(14.8f, 10.2f)
}

@Composable
private fun HeartIcon() = StrokeIcon {
    moveTo(12f, 20f)
    cubicTo(7f, 16.5f, 3.5f, 13f, 2.5f, 10f)
    cubicTo(1.5f, 6.5f, 3.5f, 4.5f, 6f, 4.5f)
    cubicTo(8f, 4.5f, 9.2f, 5.7f, 12f, 6.8f)
    cubicTo(14.8f, 5.7f, 16f, 4.5f, 18f, 4.5f)
    cubicTo(20.5f, 4.5f, 22.5f, 6.5f, 21.5f, 10f)
    cubicTo(20.5f, 13f, 17f, 16.5f, 12f, 20f)
    close()
}

@Composable
private fun WalletIcon() = StrokeIcon {
    moveTo(3f, 6.5f); lineTo(21f, 6.5f); lineTo(21f, 17f); lineTo(3f, 17f); close()
    moveTo(8f, 20f); lineTo(16f, 20f)
    moveTo(12f, 17f); lineTo(12f, 20f)
}

@Composable
private fun TrendIcon() = StrokeIcon {
    moveTo(4f, 16f); lineTo(8.5f, 11f); lineTo(12f, 14f); lineTo(20f, 7f)
    moveTo(15f, 7f); lineTo(20f, 7f); lineTo(20f, 12f)
}
