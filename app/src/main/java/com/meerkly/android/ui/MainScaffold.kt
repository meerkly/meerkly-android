package com.meerkly.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.meerkly.android.model.AuthStatus
import com.meerkly.android.ui.components.HomeIcon
import com.meerkly.android.ui.components.ListIcon
import com.meerkly.android.ui.components.PhoneIcon
import com.meerkly.android.ui.components.SlidersIcon
import com.meerkly.android.ui.nav.Destination
import com.meerkly.android.ui.nav.NavState
import com.meerkly.android.ui.nav.WindowWidth
import com.meerkly.android.ui.theme.Bone
import com.meerkly.android.ui.theme.Cream
import com.meerkly.android.ui.theme.Ink
import com.meerkly.android.ui.theme.InkSoft
import com.meerkly.android.ui.theme.Pink
import kotlinx.coroutines.delay

/** How often to re-fetch the balance while the app is open. */
private const val CREDITS_POLL_MS = 30_000L

/**
 * The signed-in shell: brand bar, tab chrome, and the current tab's content.
 *
 * Adaptive width comes from [BoxWithConstraints] measuring this container —
 * not the physical screen — so split-screen and freeform windows are right for
 * free, and the breakpoint decision stays a pure function ([WindowWidth]) that
 * unit tests can drive.
 *
 * Note this composable does NOT own the GeckoViewHost. That stays a sibling in
 * RootScreen's outer Box, deliberately outside BoxWithConstraints, which is a
 * SubcomposeLayout — the single-host invariant is too load-bearing to route
 * through subcomposition.
 */
@Composable
fun MainScaffold(
    viewModel: MainViewModel,
    auth: AuthStatus.SignedIn,
    nav: NavState,
    backEnabled: Boolean,
    onShareDiagnostics: (java.io.File) -> Unit,
    onDebugTools: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Hoisted out of DashboardScreen: both of these used to live there, so they
    // stopped the moment you left Home. The poll must keep running on every tab,
    // and the resume hook must fire wherever the user returns to — Settings
    // showing stale battery/notification state after a trip to system settings
    // was the specific bug.
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refreshCredits()
            delay(CREDITS_POLL_MS)
        }
    }
    OnResume { viewModel.refreshWorkerState() }

    BoxWithConstraints(modifier.fillMaxSize().background(Bone)) {
        val width = WindowWidth.of(maxWidth.value)

        // Only enabled when nothing higher-priority (browser panel, debug
        // screen) wants back — those are handled in RootScreen with a mutually
        // exclusive `enabled`, so neither depends on registration order.
        BackHandler(enabled = backEnabled && nav.canGoBack(width)) { nav.back(width) }

        Column(Modifier.fillMaxSize()) {
            MeerklyTopBar(auth, width)
            Row(Modifier.weight(1f)) {
                if (width.usesRail) {
                    MeerklyNavigationRail(nav.destination, nav::go)
                }
                Box(Modifier.weight(1f)) {
                    when (nav.destination) {
                        Destination.Home -> DashboardScreen(
                            viewModel = viewModel,
                            auth = auth,
                            width = width,
                        )
                        Destination.Activity -> ActivityScreen(viewModel, nav, width)
                        Destination.Devices -> DevicesScreen(viewModel, nav, width)
                        Destination.Settings -> SettingsScreen(
                            viewModel = viewModel,
                            onShareDiagnostics = onShareDiagnostics,
                            onDebugTools = onDebugTools,
                            onSignOut = {
                                // Reset first: otherwise the saved tab restores
                                // on the next sign-in.
                                nav.reset()
                                viewModel.signOut()
                            },
                        )
                    }
                }
            }
            if (!width.usesRail) {
                MeerklyNavigationBar(nav.destination, nav::go)
            }
        }
    }
}

@Composable
internal fun MeerklyNavigationBar(current: Destination, onSelect: (Destination) -> Unit) {
    // Owns the navigation-bar inset — MainActivity no longer wraps the tree in
    // a Scaffold, so without this the bar floats above the gesture pill.
    NavigationBar(
        containerColor = Cream,
        modifier = Modifier.navigationBarsPadding(),
    ) {
        Destination.entries.forEach { destination ->
            NavigationBarItem(
                selected = destination == current,
                onClick = { onSelect(destination) },
                icon = { TabIcon(destination) },
                label = {
                    Text(
                        stringResource(destination.label),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (destination == current) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Cream,
                    selectedTextColor = Ink,
                    indicatorColor = Pink,
                    unselectedIconColor = InkSoft,
                    unselectedTextColor = InkSoft,
                ),
            )
        }
    }
}

@Composable
internal fun MeerklyNavigationRail(current: Destination, onSelect: (Destination) -> Unit) {
    NavigationRail(
        containerColor = Cream,
        // safeDrawing on the rail: it hugs the start edge, which is where the
        // gesture inset lands in landscape.
        modifier = Modifier.safeDrawingPadding(),
    ) {
        Column(
            // fillMaxHeight, never fillMaxSize: NavigationRail's container is
            // widthIn(min = 80.dp), so filling width would expand the rail to
            // the whole row and starve the weighted content pane to zero.
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
        ) {
            Destination.entries.forEach { destination ->
                NavigationRailItem(
                    selected = destination == current,
                    onClick = { onSelect(destination) },
                    icon = { TabIcon(destination) },
                    label = {
                        Text(
                            stringResource(destination.label),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (destination == current) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = Cream,
                        selectedTextColor = Ink,
                        indicatorColor = Pink,
                        unselectedIconColor = InkSoft,
                        unselectedTextColor = InkSoft,
                    ),
                )
            }
        }
    }
}

@Composable
private fun TabIcon(destination: Destination) = when (destination) {
    Destination.Home -> HomeIcon()
    Destination.Activity -> ListIcon()
    Destination.Devices -> PhoneIcon()
    Destination.Settings -> SlidersIcon()
}

/** Render-harness entry point: the bar on its own, nothing else wired up. */
@Composable
internal fun MeerklyNavigationBarPreview() =
    MeerklyNavigationBar(Destination.Home) {}
