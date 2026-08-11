package com.meerkly.android.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowWidthTest {

    @Test
    fun `breakpoints sit exactly on Material's boundaries`() {
        assertEquals(WindowWidth.Compact, WindowWidth.of(0f))
        assertEquals(WindowWidth.Compact, WindowWidth.of(599f))
        assertEquals(WindowWidth.Medium, WindowWidth.of(600f))
        assertEquals(WindowWidth.Medium, WindowWidth.of(839f))
        assertEquals(WindowWidth.Expanded, WindowWidth.of(840f))
        assertEquals(WindowWidth.Expanded, WindowWidth.of(1280f))
    }

    @Test
    fun `a 10-inch tablet in portrait is Medium, so it gets one centred column`() {
        // 800dp. Two panes here would be ~300dp each — worse than one good column.
        assertEquals(WindowWidth.Medium, WindowWidth.of(800f))
        assertFalse(WindowWidth.Medium.twoPane(Destination.Activity))
    }

    @Test
    fun `two-pane only at Expanded, and only for the list screens`() {
        assertTrue(WindowWidth.Expanded.twoPane(Destination.Activity))
        assertTrue(WindowWidth.Expanded.twoPane(Destination.Devices))
        assertFalse(WindowWidth.Expanded.twoPane(Destination.Home))
        assertFalse(WindowWidth.Expanded.twoPane(Destination.Settings))
        for (d in Destination.entries) {
            assertFalse(WindowWidth.Compact.twoPane(d))
            assertFalse(WindowWidth.Medium.twoPane(d))
        }
    }

    @Test
    fun `rail replaces the bottom bar from 600dp up`() {
        assertFalse(WindowWidth.Compact.usesRail)
        assertTrue(WindowWidth.Medium.usesRail)
        assertTrue(WindowWidth.Expanded.usesRail)
    }

    @Test
    fun `content stays capped so cards never stretch`() {
        assertEquals(560, WindowWidth.Compact.contentMaxWidthDp)
        assertEquals(560, WindowWidth.Medium.contentMaxWidthDp)
        assertEquals(720, WindowWidth.Expanded.contentMaxWidthDp)
    }
}
