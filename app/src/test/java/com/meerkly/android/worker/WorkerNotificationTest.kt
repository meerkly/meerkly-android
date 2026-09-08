package com.meerkly.android.worker

import com.meerkly.android.R
import com.meerkly.android.proxy.ProxyState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Pins every [ProxyState] to honest notification copy: the ongoing
 * notification must never claim more than the dashboard would (the static
 * "Connected" lie, once removed, must not sneak back in via the shade).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkerNotificationTest {

    private val context = RuntimeEnvironment.getApplication()

    private fun textOf(state: ProxyState): String =
        WorkerNotification.build(context, state).extras
            ?.getCharSequence(android.app.Notification.EXTRA_TEXT)
            .toString()

    @Test
    fun `every proxy state maps to honest copy`() {
        assertEquals(context.getString(R.string.worker_notification_earning), textOf(ProxyState.Connected))
        assertEquals(context.getString(R.string.worker_notification_connecting), textOf(ProxyState.Connecting))
        assertEquals(context.getString(R.string.worker_notification_problem), textOf(ProxyState.Failed))
        assertEquals(context.getString(R.string.worker_notification_paused), textOf(ProxyState.Disconnected))
        assertEquals(context.getString(R.string.worker_notification_paused), textOf(ProxyState.Stopped))
    }

    @Test
    fun `only the earning state claims to be helping`() {
        val earning = context.getString(R.string.worker_notification_earning)
        val helping = ProxyState.entries.filter { textOf(it) == earning }
        assertEquals(listOf(ProxyState.Connected), helping)
    }
}
