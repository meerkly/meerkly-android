package com.meerkly.android.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meerkly.android.R
import com.meerkly.android.gateway.WorkerConnection
import com.meerkly.android.ui.theme.Cream
import com.meerkly.android.ui.theme.Emerald
import com.meerkly.android.ui.theme.Gold
import com.meerkly.android.ui.theme.InkSoft
import com.meerkly.android.ui.theme.Rose
import com.meerkly.android.ui.theme.Sand

/**
 * The worker's real socket state. This used to be a static "Connected" label,
 * which told people they were earning while the gateway was unreachable — the
 * dot only pulses when the worker is genuinely in the dispatch pool.
 */
@Composable
internal fun ConnectionChip(connection: WorkerConnection) {
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

@Composable
internal fun StatusChip(text: String, fg: Color, bg: Color) {
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
internal fun IconChip(gradient: List<Color>, icon: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .background(Brush.linearGradient(gradient), RoundedCornerShape(13.dp)),
        contentAlignment = Alignment.Center,
    ) { icon() }
}
