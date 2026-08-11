package com.meerkly.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meerkly.android.BuildConfig
import com.meerkly.android.R
import com.meerkly.android.browser.GeckoViewHost
import com.meerkly.android.model.AuthStatus
import com.meerkly.android.ui.nav.rememberNavState
import com.meerkly.android.ui.theme.Bone
import com.meerkly.android.ui.theme.Ink
import com.meerkly.android.ui.theme.InkSoft
import com.meerkly.android.ui.theme.Pink
import com.meerkly.android.ui.theme.Sand
import java.io.File

/**
 * Root switcher: Loading → AuthGate → Dashboard, plus the debug URL tester
 * behind a footer long-press in debug builds.
 *
 * The worker's GeckoView needs an attached, active surface for reliable HTML
 * extraction (see api-gateway/CLAUDE.md), and fetch jobs run regardless of the
 * visible screen — so the host stays composed at all times. Never compose two
 * hosts at once: they'd steal the single GeckoSession from each other. The
 * browser panel therefore does NOT add a second host; it re-sizes the one below
 * from the offscreen 1dp surface to a full panel, which keeps the AndroidView
 * node (and its session binding) identical across the toggle.
 */
@Composable
fun RootScreen(viewModel: MainViewModel, onShareDiagnostics: (File) -> Unit) {
    val auth by viewModel.authStatus.collectAsState()
    val browserVisible by viewModel.browserVisible.collectAsState()
    // rememberSaveable, not remember: the debug screen used to vanish on
    // rotation.
    var showDebugTools by rememberSaveable { mutableStateOf(false) }
    val nav = rememberNavState()

    // The debug tools screen brings its own full-size host, so the panel must
    // never be open at the same time.
    val panelOpen = browserVisible && !showDebugTools

    // Priorities 1 and 2 of the back chain. MainScaffold owns 3 (detail) and 4
    // (non-Home tab) behind a mutually exclusive `enabled`, so neither handler
    // depends on dispatcher registration order.
    BackHandler(enabled = panelOpen || showDebugTools) {
        if (panelOpen) viewModel.hideBrowser() else showDebugTools = false
    }

    Box(modifier = Modifier.fillMaxSize().background(Bone)) {
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
                is AuthStatus.SignedIn -> MainScaffold(
                    viewModel = viewModel,
                    auth = status,
                    nav = nav,
                    // The panel's handler outranks the scaffold's.
                    backEnabled = !panelOpen,
                    onShareDiagnostics = onShareDiagnostics,
                    onDebugTools = { if (BuildConfig.DEBUG) showDebugTools = true },
                )
            }

            // Drawn last so the panel covers the screen it overlays. Collapsed,
            // this is the same invisible 1dp surface the worker has always had.
            Column(
                modifier = if (panelOpen) {
                    Modifier.fillMaxSize().background(Bone)
                } else {
                    Modifier.align(Alignment.TopStart)
                },
            ) {
                if (panelOpen) {
                    BrowserPanelHeader(viewModel)
                }
                GeckoViewHost(
                    manager = viewModel.browserManager,
                    modifier = if (panelOpen) {
                        Modifier.fillMaxWidth().weight(1f)
                    } else {
                        Modifier.size(1.dp).alpha(0f)
                    },
                )
            }
        }
    }
}

/** Live URL + close control above the automation browser. */
@Composable
private fun BrowserPanelHeader(viewModel: MainViewModel) {
    val url by viewModel.browserManager.liveUrl.collectAsState()
    val loading by viewModel.browserManager.liveLoading.collectAsState()

    Surface(color = Sand) {
        Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.browser_panel_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = Ink,
                )
                TextButton(onClick = viewModel::hideBrowser) {
                    Text(stringResource(R.string.browser_hide), color = Pink)
                }
            }
            Text(
                text = url ?: stringResource(R.string.browser_panel_idle),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = InkSoft,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
            if (loading) {
                androidx.compose.material3.LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Pink,
                    trackColor = Sand,
                )
            }
        }
    }
}
