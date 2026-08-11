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
        val nav = NavState(Destination.Activity, activityKey = "123|https://a.test")
        assertTrue(nav.canGoBack(compact))
        assertTrue(nav.back(compact))
        assertNull(nav.activityKey)
        assertEquals(Destination.Activity, nav.destination)
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
        val nav = NavState(Destination.Activity, activityKey = "123|https://a.test")
        assertTrue(nav.back(expanded))
        assertEquals(Destination.Home, nav.destination)
        assertEquals("123|https://a.test", nav.activityKey)
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
        val nav = NavState(Destination.Activity, activityKey = "k", deviceKey = "m-1")
        nav.go(Destination.Home)
        assertNull(nav.activityKey)
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
    fun `saver round-trips destination and both selections`() {
        val nav = NavState(Destination.Devices, activityKey = "a", deviceKey = "m-9")
        val saved = with(NavState.Saver) {
            androidx.compose.runtime.saveable.SaverScope { true }.save(nav)
        }
        val restored = NavState.Saver.restore(saved!!)!!
        assertEquals(Destination.Devices, restored.destination)
        assertEquals("a", restored.activityKey)
        assertEquals("m-9", restored.deviceKey)
    }

    @Test
    fun `an unknown or missing key falls back to Home rather than crashing`() {
        assertNull(Destination.fromKey(null))
        assertNull(Destination.fromKey("nope"))
        assertEquals(Destination.Devices, Destination.fromKey("devices"))
    }
}
