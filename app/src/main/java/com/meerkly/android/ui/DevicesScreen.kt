package com.meerkly.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meerkly.android.R
import com.meerkly.android.model.DeviceEarnings
import com.meerkly.android.model.EarningsState
import com.meerkly.android.ui.components.BrandCard
import com.meerkly.android.ui.components.ContentColumn
import com.meerkly.android.ui.components.EmptyState
import com.meerkly.android.ui.components.StatusChip
import com.meerkly.android.ui.components.TwoPane
import com.meerkly.android.ui.nav.NavState
import com.meerkly.android.ui.nav.WindowWidth
import com.meerkly.android.ui.theme.Bone
import com.meerkly.android.ui.theme.Emerald
import com.meerkly.android.ui.theme.EmeraldDeep
import com.meerkly.android.ui.theme.Ink
import com.meerkly.android.ui.theme.InkSoft
import com.meerkly.android.ui.theme.Sand
import com.meerkly.android.util.Formatters

/**
 * Thin wrapper reading the ViewModel: pulls the current earnings out of
 * [MainViewModel.earnings] and delegates everything else to [DevicesContent],
 * which takes plain state and no ViewModel so it can render under Robolectric
 * (a ViewModel would drag in AppGraph and a live ProxyClient that needs a
 * native library).
 */
@Composable
fun DevicesScreen(viewModel: MainViewModel, nav: NavState, width: WindowWidth) {
    val earnings by viewModel.earnings.collectAsState()
    val loaded = (earnings as? EarningsState.Loaded)?.earnings
    DevicesContent(
        devices = loaded?.devices.orEmpty(),
        thisDeviceId = viewModel.deviceId,
        width = width,
        windowDays = loaded?.devicesWindowDays ?: 30,
    )
}

/**
 * The devices list: one row per device, each carrying figures that are
 * windowed (not lifetime) and include unsettled money — unlike Home's account
 * totals. They will not add up to Home's lifetime balance and are not meant
 * to; the header says so explicitly rather than leaving that unlabelled.
 *
 * [windowDays] defaults to 30 only so callers that genuinely have no earnings
 * yet (or a render harness) have something to show; [DevicesScreen] always
 * passes the server's real [com.meerkly.android.model.Earnings.devicesWindowDays].
 */
@Composable
fun DevicesContent(
    devices: List<DeviceEarnings>,
    thisDeviceId: String,
    width: WindowWidth,
    windowDays: Int = 30,
) {
    if (devices.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.devices_empty_title),
            note = stringResource(R.string.devices_empty_note),
        )
        return
    }

    if (width == WindowWidth.Expanded) {
        var selectedId by remember(devices) {
            mutableStateOf(devices.firstOrNull { it.deviceId == thisDeviceId }?.deviceId ?: devices.first().deviceId)
        }
        TwoPane(
            list = {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    DeviceListBody(devices, thisDeviceId, windowDays, selectedId) { selectedId = it }
                }
            },
            detail = {
                val selected = devices.firstOrNull { it.deviceId == selectedId }
                Column(Modifier.fillMaxSize().padding(20.dp)) {
                    if (selected != null) {
                        DeviceDetail(selected, selected.deviceId == thisDeviceId, windowDays)
                    }
                }
            },
        )
    } else {
        Column(
            modifier = Modifier.fillMaxSize().background(Bone).verticalScroll(rememberScrollState()),
        ) {
            ContentColumn(maxWidth = width.contentMaxWidthDp.dp) {
                DeviceListBody(devices, thisDeviceId, windowDays, selectedId = null, onSelect = {})
            }
        }
    }
}

@Composable
private fun DeviceListBody(
    devices: List<DeviceEarnings>,
    thisDeviceId: String,
    windowDays: Int,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    // Per-device figures are windowed and include unsettled money; the account
    // totals on Home are lifetime and settled-only. They deliberately do not
    // reconcile — this header is what stops that reading as "money went
    // missing".
    Text(
        text = stringResource(R.string.days_window, windowDays),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = InkSoft,
        modifier = Modifier.padding(bottom = 2.dp),
    )
    devices.forEach { device ->
        DeviceRow(
            device = device,
            isThisDevice = device.deviceId == thisDeviceId,
            selected = device.deviceId == selectedId,
            onClick = { onSelect(device.deviceId) },
        )
    }
}

@Composable
private fun DeviceRow(device: DeviceEarnings, isThisDevice: Boolean, selected: Boolean, onClick: () -> Unit) {
    BrandCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (selected) Modifier.background(Sand.copy(alpha = 0.35f)) else Modifier,
                )
                .clickableNoRipple(onClick)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OnlineDot(device.online)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(device.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Ink)
                    if (isThisDevice) {
                        StatusChip(
                            text = stringResource(R.string.devices_this_device),
                            fg = EmeraldDeep,
                            bg = Emerald.copy(alpha = 0.12f),
                        )
                    }
                }
                Text(Formatters.bytes(device.bytes30d), style = MaterialTheme.typography.bodySmall, color = InkSoft)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(Formatters.usd(device.usd30d), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Ink)
                // The server settles hours on a lag; a pending row's figure is
                // an estimate and must never be presented as final.
                if (device.pending) {
                    Text(stringResource(R.string.devices_pending_note), style = MaterialTheme.typography.labelSmall, color = InkSoft)
                }
            }
        }
    }
}

@Composable
private fun DeviceDetail(device: DeviceEarnings, isThisDevice: Boolean, windowDays: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OnlineDot(device.online, size = 12.dp)
        Text(device.label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink)
        if (isThisDevice) {
            StatusChip(
                text = stringResource(R.string.devices_this_device),
                fg = EmeraldDeep,
                bg = Emerald.copy(alpha = 0.12f),
            )
        }
    }
    Text(
        text = stringResource(R.string.days_window, windowDays),
        style = MaterialTheme.typography.labelSmall,
        color = InkSoft,
        modifier = Modifier.padding(top = 12.dp),
    )
    Text(
        text = Formatters.usd(device.usd30d),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = Ink,
        modifier = Modifier.padding(top = 4.dp),
    )
    if (device.pending) {
        Text(stringResource(R.string.devices_pending_note), style = MaterialTheme.typography.bodySmall, color = InkSoft)
    }
    Text(
        text = Formatters.bytes(device.bytes30d),
        style = MaterialTheme.typography.bodySmall,
        color = InkSoft,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun OnlineDot(online: Boolean, size: androidx.compose.ui.unit.Dp = 10.dp) {
    androidx.compose.foundation.layout.Box(
        Modifier.size(size).background(if (online) Emerald else Sand, CircleShape),
    )
}

/** A click target with no ripple/indication — these rows sit on a card, not a list surface. */
@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.then(
    Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    ),
)
