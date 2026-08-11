package com.meerkly.android.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meerkly.android.R
import com.meerkly.android.ui.theme.Display
import com.meerkly.android.ui.theme.Bone
import com.meerkly.android.ui.theme.Cream
import com.meerkly.android.ui.theme.InkSoft
import com.meerkly.android.ui.theme.Pink
import com.meerkly.android.ui.theme.RoseDeep
import com.meerkly.android.ui.theme.RoseSoft
import com.meerkly.android.ui.theme.Sand

/** The first thing a signed-out user sees. Meerkly waves hello. */
@Composable
fun AuthGateScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val signingIn by viewModel.signingIn.collectAsState()
    val error by viewModel.signInError.collectAsState()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.onSignInResult(result.data)
    }
    val noBrowserMessage = stringResource(R.string.gate_no_browser)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Bone)
            // Owns its insets: MainActivity no longer wraps the tree in a
            // Scaffold. safeDrawing (not just statusBars) because this card is
            // centred and scrollable — it must clear the gesture pill too.
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = Cream,
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Sand),
            shadowElevation = 2.dp,
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MeerklyMascot(modifier = Modifier.size(width = 140.dp, height = 168.dp))
                Text(
                    text = "meerkly",
                    fontFamily = Display,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                )
                Text(
                    text = stringResource(R.string.gate_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = Display,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.gate_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = {
                        viewModel.onSignInLaunched()
                        try {
                            launcher.launch(viewModel.signInIntent())
                        } catch (_: ActivityNotFoundException) {
                            viewModel.onSignInFailedToLaunch(noBrowserMessage)
                        }
                    },
                    enabled = !signingIn,
                    colors = ButtonDefaults.buttonColors(containerColor = Pink, contentColor = Cream),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Text(
                        text = stringResource(if (signingIn) R.string.gate_cta_busy else R.string.gate_cta),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.gate_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                )
                error?.let {
                    Surface(
                        color = RoseSoft,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = RoseDeep,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}
