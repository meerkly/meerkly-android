package com.meerkly.android.ui

import com.meerkly.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The checklist is the only thing standing between a half-configured install
 * and a user who thinks Meerkly is earning when it isn't — so every
 * combination is pinned here, especially the ones that decide whether the card
 * can ever disappear.
 */
class SetupChecklistTest {

    private fun steps(
        deviceLinked: Boolean = true,
        notificationsGranted: Boolean = true,
        notificationsPermanentlyDenied: Boolean = false,
        batteryExempt: Boolean = true,
        sdkInt: Int = 34,
    ) = SetupChecklist.steps(
        deviceLinked = deviceLinked,
        notificationsGranted = notificationsGranted,
        notificationsPermanentlyDenied = notificationsPermanentlyDenied,
        batteryExempt = batteryExempt,
        sdkInt = sdkInt,
    )

    private fun step(list: List<SetupStep>, title: Int) = list.first { it.title == title }

    @Test
    fun `a fully configured install has nothing left to show`() {
        val s = steps()
        assertTrue(SetupChecklist.allDone(s))
        assertEquals(4, SetupChecklist.doneCount(s))
        // No actionable rows once everything is done.
        assertTrue(s.all { it.action == null })
    }

    @Test
    fun `signed in is always ticked - the dashboard only exists when signed in`() {
        assertTrue(step(steps(deviceLinked = false), R.string.setup_signed_in_title).done)
    }

    @Test
    fun `an unlinked device blocks completion but offers no button`() {
        val s = steps(deviceLinked = false)
        assertFalse(SetupChecklist.allDone(s))
        val linked = step(s, R.string.setup_linked_title)
        assertFalse(linked.done)
        // Linking heals itself via the coordinator; a button the user can't
        // usefully press would just look broken.
        assertNull(linked.action)
    }

    @Test
    fun `notifications are pre-granted below API 33`() {
        val s = steps(notificationsGranted = false, sdkInt = 32)
        val notif = step(s, R.string.setup_notifications_title)
        assertTrue(notif.done)
        assertNull(notif.action)
        assertTrue(SetupChecklist.allDone(s))
    }

    @Test
    fun `a missing notification permission asks the system first`() {
        val notif = step(steps(notificationsGranted = false), R.string.setup_notifications_title)
        assertFalse(notif.done)
        assertEquals(SetupAction.RequestNotifications, notif.action)
    }

    @Test
    fun `a permanently denied permission routes to settings, not a dead dialog`() {
        // Two refusals make the system dialog a no-op — without this the step
        // could never be completed and the card would never hide.
        val notif = step(
            steps(notificationsGranted = false, notificationsPermanentlyDenied = true),
            R.string.setup_notifications_title,
        )
        assertEquals(SetupAction.OpenNotificationSettings, notif.action)
    }

    @Test
    fun `granted beats permanently-denied if the user fixes it in settings`() {
        val notif = step(
            steps(notificationsGranted = true, notificationsPermanentlyDenied = true),
            R.string.setup_notifications_title,
        )
        assertTrue(notif.done)
        assertNull(notif.action)
    }

    @Test
    fun `a battery-throttled install is incomplete and offers the exemption`() {
        val s = steps(batteryExempt = false)
        assertFalse(SetupChecklist.allDone(s))
        assertEquals(3, SetupChecklist.doneCount(s))
        assertEquals(
            SetupAction.RequestBatteryExemption,
            step(s, R.string.setup_battery_title).action,
        )
    }

    @Test
    fun `progress counts completed steps for the header`() {
        assertEquals(
            2,
            SetupChecklist.doneCount(steps(deviceLinked = false, batteryExempt = false)),
        )
    }
}
