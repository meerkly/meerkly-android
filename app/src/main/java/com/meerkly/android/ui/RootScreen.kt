package com.meerkly.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.meerkly.android.BuildConfig
import com.meerkly.android.browser.GeckoViewHost
import com.meerkly.android.model.AuthStatus
import com.meerkly.android.ui.theme.Bone
import com.meerkly.android.ui.theme.Pink
import java.io.File

/**
 * Root switcher: Loading → AuthGate → Dashboard, plus the debug URL tester
 * behind a footer long-press in debug builds.
 *
 * The worker's GeckoView needs an attached, active surface for reliable HTML
 * extraction (see api-gateway/CLAUDE.md), and fetch jobs run regardless of the
 * visible screen — so a 1dp invisible host stays composed whenever the debug
 * screen (which brings its own full-size host) isn't showing. Never compose
 * two hosts at once: they'd steal the single GeckoSession from each other.
 */
@Composable
fun RootScreen(viewModel: MainViewModel, onShareDiagnostics: (File) -> Unit) {
    val auth by viewModel.authStatus.collectAsState()
    var showDebugTools by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Bone)) {
        if (!showDebugTools) {
            GeckoViewHost(
                manager = viewModel.browserManager,
                modifier = Modifier
                    .size(1.dp)
                    .alpha(0f)
                    .align(Alignment.TopStart),
            )
        }

        if (showDebugTools) {
            DebugToolsScreen(
                viewModel = viewModel,
                onShareDiagnostics = onShareDiagnostics,
                onClose = { showDebugTools = false },
            )
        } else {
            when (val status = auth) {
                AuthStatus.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Pink)
                }
                AuthStatus.SignedOut -> AuthGateScreen(viewModel)
                is AuthStatus.SignedIn -> DashboardScreen(
                    viewModel = viewModel,
                    auth = status,
                    onFooterLongPress = { if (BuildConfig.DEBUG) showDebugTools = true },
                )
            }
        }
    }
}
