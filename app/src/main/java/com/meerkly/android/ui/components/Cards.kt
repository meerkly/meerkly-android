package com.meerkly.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meerkly.android.ui.theme.Display
import com.meerkly.android.ui.theme.Cream
import com.meerkly.android.ui.theme.Ink
import com.meerkly.android.ui.theme.InkSoft
import com.meerkly.android.ui.theme.Sand

@Composable
internal fun BrandCard(content: @Composable () -> Unit) {
    Surface(
        color = Cream,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Sand),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) { content() }
}

@Composable
internal fun EarnCard(label: String, value: String, note: String, chip: @Composable () -> Unit) {
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
                fontFamily = Display,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                color = Ink,
            )
            Text(text = note, style = MaterialTheme.typography.bodySmall, color = InkSoft)
        }
    }
}

@Composable
internal fun ReassuranceCard(title: String, note: String, chip: @Composable () -> Unit) {
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
