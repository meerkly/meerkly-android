package com.meerkly.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meerkly.android.R
import com.meerkly.android.model.AuthStatus
import com.meerkly.android.ui.components.StatusChip
import com.meerkly.android.ui.theme.Display
import com.meerkly.android.ui.theme.Cream
import com.meerkly.android.ui.theme.Emerald
import com.meerkly.android.ui.theme.EmeraldDeep
import com.meerkly.android.ui.theme.InkSoft

/**
 * Brand bar shown above every tab.
 *
 * Sign-out used to live here; it moved to Settings, which is where people look
 * for it and which frees the bar to be purely identity — wordmark, who you're
 * signed in as, and whether this device is linked.
 */
@Composable
fun MeerklyTopBar(viewModel: MainViewModel, auth: AuthStatus.SignedIn) {
    Surface(color = Cream.copy(alpha = 0.9f)) {
        // Owns its status-bar inset: MainActivity no longer wraps the tree in a
        // Scaffold, so the surface must extend under the status bar itself.
        Column(Modifier.statusBarsPadding()) {
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
                    fontFamily = Display,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                Spacer(Modifier.weight(1f))
                if (auth.deviceLinked) {
                    StatusChip(
                        text = stringResource(R.string.device_linked),
                        fg = EmeraldDeep,
                        bg = Emerald.copy(alpha = 0.1f),
                    )
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
