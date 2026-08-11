package com.meerkly.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.meerkly.android.ui.MeerklyMascot
import com.meerkly.android.ui.theme.Display
import com.meerkly.android.ui.theme.Ink
import com.meerkly.android.ui.theme.InkSoft

/**
 * What a list shows before it has anything in it. Always a reason and a next
 * step — a blank screen reads as broken, and these lists are legitimately
 * empty on a fresh install.
 */
@Composable
internal fun EmptyState(
    title: String,
    note: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MeerklyMascot(modifier = Modifier.size(width = 96.dp, height = 116.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = Display,
            fontWeight = FontWeight.Bold,
            color = Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = note,
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
