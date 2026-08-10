package com.meerkly.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meerkly.android.R
import com.meerkly.android.ui.theme.Cream
import com.meerkly.android.ui.theme.Emerald
import com.meerkly.android.ui.theme.EmeraldDeep
import com.meerkly.android.ui.theme.Ink
import com.meerkly.android.ui.theme.InkSoft
import com.meerkly.android.ui.theme.Sand

/**
 * Onboarding checklist. Renders only while setup is incomplete — the caller
 * checks [SetupChecklist.allDone], so a fully set-up user never sees it again.
 *
 * Completed steps stay listed (ticked) rather than disappearing one by one:
 * the point is showing how close "done" is, and a list that shrinks as you
 * work loses that.
 */
@Composable
fun GettingStartedCard(
    steps: List<SetupStep>,
    onAction: (SetupAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Cream,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Sand),
        modifier = modifier,
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.setup_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                )
                Text(
                    stringResource(
                        R.string.setup_progress,
                        SetupChecklist.doneCount(steps),
                        steps.size,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSoft,
                )
            }
            steps.forEach { step -> StepRow(step, onAction) }
        }
    }
}

@Composable
private fun StepRow(step: SetupStep, onAction: (SetupAction) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StepMark(done = step.done)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                stringResource(step.title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                // Finished steps recede so the eye lands on what's left.
                color = if (step.done) InkSoft else Ink,
            )
            Text(
                stringResource(step.note),
                style = MaterialTheme.typography.labelSmall,
                color = InkSoft,
            )
        }
        step.action?.let { action ->
            OutlinedButton(
                onClick = { onAction(action) },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Sand),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    stringResource(R.string.setup_allow),
                    color = EmeraldDeep,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** Filled tick when done, hollow ring when still outstanding. */
@Composable
private fun StepMark(done: Boolean) {
    if (!done) {
        Box(
            Modifier
                .size(20.dp)
                .background(Cream, CircleShape),
        ) {
            Canvas(Modifier.size(20.dp)) {
                drawCircle(
                    color = Sand,
                    radius = size.minDimension / 2 - 1.dp.toPx(),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                )
            }
        }
        return
    }
    Box(
        modifier = Modifier
            .size(20.dp)
            .background(Emerald, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(12.dp)) {
            val w = size.width
            val h = size.height
            drawLine(
                color = Cream,
                start = Offset(w * 0.16f, h * 0.54f),
                end = Offset(w * 0.42f, h * 0.78f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Cream,
                start = Offset(w * 0.42f, h * 0.78f),
                end = Offset(w * 0.86f, h * 0.24f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}
