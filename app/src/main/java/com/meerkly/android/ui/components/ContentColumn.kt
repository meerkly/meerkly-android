package com.meerkly.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Centred, width-capped content column.
 *
 * Every screen body goes through this so none of them can forget the cap: at
 * tablet widths an uncapped column stretches cards to 1200dp, which is the
 * single clearest tell that an app is a blown-up phone layout.
 */
@Composable
internal fun ContentColumn(
    modifier: Modifier = Modifier,
    maxWidth: Dp = 560.dp,
    horizontal: Dp = 20.dp,
    verticalSpacing: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .padding(horizontal = horizontal, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            content = content,
        )
    }
}
