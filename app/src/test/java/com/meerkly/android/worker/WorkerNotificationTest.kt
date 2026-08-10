package com.meerkly.android.worker

import com.meerkly.android.R
import com.meerkly.android.gateway.WorkerConnection
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins every [WorkerConnection] to honest notification copy: the ongoing
 * notification must never claim more than the dashboard would (the static
 * "Connected" lie, once removed, must not sneak back in via the shade).
 */
class WorkerNotificationTest {

    @Test
    fun `every connection state maps to honest copy`() {
        assertEquals(R.string.notif_text_connected, WorkerNotification.textFor(WorkerConnection.Connected))
        assertEquals(R.string.notif_text_connecting, WorkerNotification.textFor(WorkerConnection.Connecting))
        assertEquals(R.string.notif_text_connecting, WorkerNotification.textFor(WorkerConnection.Registering))
        assertEquals(R.string.notif_text_offline, WorkerNotification.textFor(WorkerConnection.Offline))
        assertEquals(R.string.notif_text_unpaired, WorkerNotification.textFor(WorkerConnection.Unpaired))
        // Shouldn't be visible in practice (launcher never starts a gateway-less
        // build; Disconnected is transient) — least-wrong copy if they are.
        assertEquals(R.string.notif_text_connecting, WorkerNotification.textFor(WorkerConnection.Disabled))
        assertEquals(R.string.notif_text_connecting, WorkerNotification.textFor(WorkerConnection.Disconnected))
    }

    @Test
    fun `only the earning state claims to be helping`() {
        val helping = WorkerConnection.entries.filter {
            WorkerNotification.textFor(it) == R.string.notif_text_connected
        }
        assertEquals(listOf(WorkerConnection.Connected), helping)
    }
}
