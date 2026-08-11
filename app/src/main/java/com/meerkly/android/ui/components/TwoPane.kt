package com.meerkly.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meerkly.android.ui.theme.Cream
import com.meerkly.android.ui.theme.Sand

/**
 * List beside detail, for the widest layouts only.
 *
 * Hand-rolled rather than `ListDetailPaneScaffold`: that lives in a separate
 * Maven group with its own BOM and ships its own back-navigation model, which
 * would compete with this app's BackHandler chain (browser panel → debug
 * screen → detail → tab → system). Two weights in a Row is the whole feature.
 */
@Composable
internal fun TwoPane(
    modifier: Modifier = Modifier,
    list: @Composable () -> Unit,
    detail: @Composable () -> Unit,
) {
    Row(modifier.fillMaxSize().widthIn(max = 1100.dp)) {
        Box(Modifier.weight(0.38f)) { list() }
        VerticalDivider(color = Sand)
        Box(Modifier.weight(0.62f).background(Cream)) { detail() }
    }
}
