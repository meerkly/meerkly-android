package com.meerkly.android.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Where we are: the current tab, plus which row is selected on each of the two
 * list screens.
 *
 * Hand-rolled rather than navigation-compose. Four destinations with no
 * arguments and no deep links don't need a NavHost, and the decider is the
 * single-GeckoViewHost invariant: a NavHost swaps composables per destination,
 * so the host would have to live outside it anyway, while NavHost's own
 * back-stack dispatcher would compete with the browser panel's BackHandler.
 * Keeping it as plain data also makes [back] a unit-testable function, which a
 * NavHost can never be.
 *
 * Selections are stored as **stable string keys**, never objects:
 * NavigationResult has no id, and its index in the newest-first ring shifts
 * every time a crawl lands.
 */
@Stable
class NavState(
    destination: Destination = Destination.Home,
    activityKey: String? = null,
    deviceKey: String? = null,
) {
    var destination by mutableStateOf(destination)
        private set
    var activityKey by mutableStateOf(activityKey)
    var deviceKey by mutableStateOf(deviceKey)

    /** The selection the current tab would pop, or null when there's nothing to pop. */
    private fun selection(width: WindowWidth): String? = when {
        // Two-pane: both list and detail are already visible, so there is no
        // "detail screen" to back out of.
        width.twoPane(destination) -> null
        destination == Destination.Activity -> activityKey
        destination == Destination.Devices -> deviceKey
        else -> null
    }

    fun canGoBack(width: WindowWidth): Boolean =
        selection(width) != null || destination != Destination.Home

    /** True when the event was consumed; false lets the system handle it (exit). */
    fun back(width: WindowWidth): Boolean = when {
        selection(width) != null -> {
            activityKey = null
            deviceKey = null
            true
        }
        destination != Destination.Home -> {
            destination = Destination.Home
            true
        }
        else -> false
    }

    /** Switching tabs drops any selection, so returning to a tab starts clean. */
    fun go(target: Destination) {
        destination = target
        activityKey = null
        deviceKey = null
    }

    /** Sign-out must not leave a saved tab to restore into on the next sign-in. */
    fun reset() = go(Destination.Home)

    companion object {
        val Saver = listSaver<NavState, Any?>(
            save = { listOf(it.destination.key, it.activityKey, it.deviceKey) },
            restore = {
                NavState(
                    destination = Destination.fromKey(it[0] as? String) ?: Destination.Home,
                    activityKey = it[1] as? String,
                    deviceKey = it[2] as? String,
                )
            },
        )
    }
}

@Composable
fun rememberNavState(start: Destination = Destination.Home): NavState =
    rememberSaveable(saver = NavState.Saver) { NavState(start) }
