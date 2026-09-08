package com.meerkly.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.meerkly.android.model.AuthStatus
import com.meerkly.android.ui.nav.Destination
import com.meerkly.android.ui.nav.rememberNavState
import com.meerkly.android.ui.theme.Bone
import com.meerkly.android.ui.theme.Pink
import java.io.File

/**
 * Root switcher: Loading → AuthGate → Dashboard.
 *
 * Used to also host the worker's persistent GeckoView browser panel and a
 * debug-tools screen; both went with the browser-driven worker (Task 2). The
 * proxy SDK has no surface that needs to stay composed here.
 */
@Composable
fun RootScreen(
    viewModel: MainViewModel,
    onShareDiagnostics: (File) -> Unit,
    startDestination: Destination = Destination.Home,
) {
    val auth by viewModel.authStatus.collectAsState()
    val nav = rememberNavState(startDestination)

    Box(modifier = Modifier.fillMaxSize().background(Bone)) {
        when (val status = auth) {
            AuthStatus.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Pink)
            }
            AuthStatus.SignedOut -> AuthGateScreen(viewModel)
            is AuthStatus.SignedIn -> MainScaffold(
                viewModel = viewModel,
                auth = status,
                nav = nav,
                onShareDiagnostics = onShareDiagnostics,
            )
        }
    }
}
