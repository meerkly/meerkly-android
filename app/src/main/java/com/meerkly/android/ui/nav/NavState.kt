package com.meerkly.android.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Where we are: the current tab, plus which device (if any) is selected on
 * the Devices screen.
 *
 * Hand-rolled rather than navigation-compose. Three destinations with no
 * arguments and no deep links don't need a NavHost, and keeping navigation as
 * plain data makes [back] a unit-testable function, which a NavHost can never
 * be.
 *
 * [deviceKey] is the selected device's stable id, not an index or position.
 */
@Stable
class NavState(
    destination: Destination = Destination.Home,
    deviceKey: String? = null,
) {
    var destination by mutableStateOf(destination)
        private set
    var deviceKey by mutableStateOf(deviceKey)

    /** The selection the current tab would pop, or null when there's nothing to pop. */
    private fun selection(width: WindowWidth): String? = when {
        // Two-pane: both list and detail are already visible, so there is no
        // "detail screen" to back out of.
        width.twoPane(destination) -> null
        destination == Destination.Devices -> deviceKey
        else -> null
    }

    fun canGoBack(width: WindowWidth): Boolean =
        selection(width) != null || destination != Destination.Home

    /** True when the event was consumed; false lets the system handle it (exit). */
    fun back(width: WindowWidth): Boolean = when {
        selection(width) != null -> {
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
        deviceKey = null
    }

    /** Sign-out must not leave a saved tab to restore into on the next sign-in. */
    fun reset() = go(Destination.Home)

    companion object {
        val Saver = listSaver<NavState, Any?>(
            save = { listOf(it.destination.key, it.deviceKey) },
            // The saved list is untrusted input, not a value this code wrote and
            // controls: a future refactor of the saved shape, or a list that
            // simply doesn't match what's expected, must not crash a restore.
            // Losing the tab/selection and landing on Home is a non-event; an
            // IndexOutOfBoundsException on the screen the user is looking at is
            // not. This deliberately does not attempt to migrate an older
            // (e.g. three-element, pre-2.0) layout — rememberSaveable's Bundle
            // isn't reliably carried across a package replace, so that state
            // doesn't survive an upgrade anyway.
            restore = {
                val destination = Destination.fromKey(it.getOrNull(0) as? String)
                if (destination == null || it.size != 2) {
                    // Either the destination key or the shape is unrecognised:
                    // don't trust the rest of the list either, just start clean.
                    NavState()
                } else {
                    NavState(destination = destination, deviceKey = it.getOrNull(1) as? String)
                }
            },
        )
    }
}

@Composable
fun rememberNavState(start: Destination = Destination.Home): NavState =
    rememberSaveable(saver = NavState.Saver) { NavState(start) }
