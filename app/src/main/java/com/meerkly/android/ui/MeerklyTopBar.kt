package com.meerkly.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meerkly.android.R
import com.meerkly.android.model.AuthStatus
import com.meerkly.android.ui.components.StatusChip
import com.meerkly.android.ui.nav.WindowWidth
import com.meerkly.android.ui.theme.Cream
import com.meerkly.android.ui.theme.Emerald
import com.meerkly.android.ui.theme.EmeraldDeep
import com.meerkly.android.ui.theme.Ink
import com.meerkly.android.ui.theme.InkSoft
import com.meerkly.android.ui.theme.Sand

/** Horizontal gutter shared with [com.meerkly.android.ui.components.ContentColumn]. */
private val Gutter = 20.dp

/**
 * Brand bar shown above every tab.
 *
 * Sign-out used to live here; it moved to Settings, which is where people look
 * for it and which frees the bar to be purely identity — wordmark, who you're
 * signed in as, and whether this device is linked.
 */
@Composable
fun MeerklyTopBar(auth: AuthStatus.SignedIn, width: WindowWidth) {
    Surface(color = Cream) {
        // Owns its status-bar inset: MainActivity no longer wraps the tree in a
        // Scaffold, so the surface must extend under the status bar itself.
        Column(Modifier.statusBarsPadding()) {
            // Centred and capped to the same width as the page body, so the
            // wordmark lines up with the cards underneath instead of hugging
            // the screen edge on a tablet.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier = Modifier
                        .widthIn(max = width.contentMaxWidthDp.dp)
                        .fillMaxWidth()
                        // Same 20dp gutter as ContentColumn — it was 16dp, which
                        // left the wordmark 4dp adrift of everything below it.
                        .padding(horizontal = Gutter)
                        .padding(top = 12.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        BrandCoin(modifier = Modifier.size(26.dp))
                        Text(
                            text = "meerkly",
                            style = MaterialTheme.typography.titleLarge,
                            color = Ink,
                        )
                        Spacer(Modifier.weight(1f))
                        if (auth.deviceLinked) {
                            StatusChip(
                                text = stringResource(R.string.device_linked),
                                fg = EmeraldDeep,
                                bg = Emerald.copy(alpha = 0.12f),
                            )
                        }
                    }
                    Text(
                        text = auth.email,
                        style = MaterialTheme.typography.labelSmall,
                        color = InkSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // Indented past the coin so it reads as a subtitle of the
                        // wordmark rather than a second, unrelated line.
                        modifier = Modifier.padding(start = 26.dp + 9.dp),
                    )
                }
            }
            // A hairline instead of the old translucent fill: the bar was
            // Cream-at-90%, which muddied against the Bone body and gave the
            // header no defined edge.
            HorizontalDivider(color = Sand.copy(alpha = 0.7f), thickness = 1.dp)
        }
    }
}
