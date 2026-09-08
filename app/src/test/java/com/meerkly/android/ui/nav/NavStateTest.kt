package com.meerkly.android.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The back chain is the part users notice when it's wrong, and it's the one
 * piece of navigation a NavHost would have hidden inside a library. Pinned here
 * in full.
 */
class NavStateTest {

    private val compact = WindowWidth.Compact
    private val expanded = WindowWidth.Expanded

    @Test
    fun `starts at Home with nothing selected`() {
        val nav = NavState()
        assertEquals(Destination.Home, nav.destination)
        assertFalse(nav.canGoBack(compact))
    }

    @Test
    fun `back from a compact detail clears the selection, not the tab`() {
        val nav = NavState(Destination.Devices, deviceKey = "m-1")
        assertTrue(nav.canGoBack(compact))
        assertTrue(nav.back(compact))
        assertNull(nav.deviceKey)
        assertEquals(Destination.Devices, nav.destination)
    }

    @Test
    fun `back from a tab with no selection returns Home`() {
        val nav = NavState(Destination.Settings)
        assertTrue(nav.back(compact))
        assertEquals(Destination.Home, nav.destination)
    }

    @Test
    fun `back at Home is not consumed, so the system exits`() {
        val nav = NavState()
        assertFalse(nav.back(compact))
        assertEquals(Destination.Home, nav.destination)
    }

    @Test
    fun `two-pane back skips the selection because both panes are visible`() {
        // Expanded shows list AND detail, so there is no detail screen to pop —
        // back should leave the tab instead of silently clearing the highlight.
        val nav = NavState(Destination.Devices, deviceKey = "m-1")
        assertTrue(nav.back(expanded))
        assertEquals(Destination.Home, nav.destination)
        assertEquals("m-1", nav.deviceKey)
    }

    @Test
    fun `the full compact chain unwinds one step at a time`() {
        val nav = NavState(Destination.Devices, deviceKey = "m-1")
        assertTrue(nav.back(compact))               // clear selection
        assertEquals(Destination.Devices, nav.destination)
        assertTrue(nav.back(compact))               // leave tab
        assertEquals(Destination.Home, nav.destination)
        assertFalse(nav.back(compact))              // exit
    }

    @Test
    fun `switching tabs drops selections so a tab is never re-entered mid-detail`() {
        val nav = NavState(Destination.Devices, deviceKey = "m-1")
        nav.go(Destination.Home)
        assertNull(nav.deviceKey)
    }

    @Test
    fun `reset sends sign-out back to Home`() {
        val nav = NavState(Destination.Settings, deviceKey = "m-1")
        nav.reset()
        assertEquals(Destination.Home, nav.destination)
        assertNull(nav.deviceKey)
    }

    @Test
    fun `saver round-trips destination and selection`() {
        val nav = NavState(Destination.Devices, deviceKey = "m-9")
        val saved = with(NavState.Saver) {
            androidx.compose.runtime.saveable.SaverScope { true }.save(nav)
        }
        val restored = NavState.Saver.restore(saved!!)!!
        assertEquals(Destination.Devices, restored.destination)
        assertEquals("m-9", restored.deviceKey)
    }

    @Test
    fun `an unknown or missing key falls back to Home rather than crashing`() {
        assertNull(Destination.fromKey(null))
        assertNull(Destination.fromKey("nope"))
        assertEquals(Destination.Devices, Destination.fromKey("devices"))
    }

    @Test
    fun `there are three tabs and Activity is not one of them`() {
        assertEquals(
            listOf(Destination.Home, Destination.Devices, Destination.Settings),
            Destination.entries.toList(),
        )
    }

    @Test
    fun `a saved Activity key from 1_x falls back rather than crashing`() {
        // rememberSaveable restores across an app upgrade, so a 1.x install
        // reopening on 2.0 hands us a key that no longer exists.
        assertNull(Destination.fromKey("activity"))
    }

    @Test
    fun `restore from a list of unexpected length falls back to Home instead of crashing`() {
        // The saved list is untrusted input: a shorter list must not throw
        // IndexOutOfBoundsException, and a longer one (e.g. a stale 3-element
        // pre-2.0 layout) must not misread its extra field as deviceKey.
        val tooShort = NavState.Saver.restore(emptyList<Any?>())!!
        assertEquals(Destination.Home, tooShort.destination)
        assertNull(tooShort.deviceKey)

        val tooLong = NavState.Saver.restore(listOf("devices", "m-1", "old-activity-key"))!!
        assertEquals(Destination.Devices, tooLong.destination)
        assertNull(tooLong.deviceKey)
    }

    @Test
    fun `restore with an unknown destination key falls back to Home with no selection`() {
        val restored = NavState.Saver.restore(listOf("nope", "m-1"))!!
        assertEquals(Destination.Home, restored.destination)
        assertNull(restored.deviceKey)
    }
}
