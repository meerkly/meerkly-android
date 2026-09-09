package com.meerkly.android.net

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkChangeMonitorTest {

    private companion object {
        const val WIFI = 11L
        const val CELLULAR = 22L
        const val OTHER_WIFI = 33L
        const val DEBOUNCE_MS = 3_000L
    }

    /**
     * `registerDefaultNetworkCallback` delivers the network that is already in
     * use the moment it is registered. Treating that as a change would drop a
     * healthy tunnel every time the worker starts.
     */
    @Test
    fun `the network already in use at registration is not a change`() = runTest {
        var reconnects = 0
        val monitor = monitor(TestScope(StandardTestDispatcher(testScheduler))) { reconnects++ }

        monitor.report(WIFI)
        advanceUntilIdle()

        assertEquals(0, reconnects)
    }

    /**
     * The framework re-delivers the same network on capability and link-property
     * updates, which a phone produces constantly while sitting still.
     */
    @Test
    fun `the same network reported again is not a change`() = runTest {
        var reconnects = 0
        val monitor = monitor(TestScope(StandardTestDispatcher(testScheduler))) { reconnects++ }

        monitor.report(WIFI)
        monitor.report(WIFI)
        monitor.report(WIFI)
        advanceUntilIdle()

        assertEquals(0, reconnects)
    }

    @Test
    fun `moving to another network reconnects once`() = runTest {
        var reconnects = 0
        val monitor = monitor(TestScope(StandardTestDispatcher(testScheduler))) { reconnects++ }

        monitor.report(WIFI)
        monitor.report(CELLULAR)
        advanceUntilIdle()

        assertEquals(1, reconnects)
    }

    /** Nothing should happen until the device has stopped moving between networks. */
    @Test
    fun `a reconnect waits out the debounce`() = runTest {
        var reconnects = 0
        val monitor = monitor(TestScope(StandardTestDispatcher(testScheduler))) { reconnects++ }

        monitor.report(WIFI)
        monitor.report(CELLULAR)
        advanceTimeBy(DEBOUNCE_MS - 1)

        assertEquals(0, reconnects)
    }

    /**
     * Walking out of wifi range produces several default-network changes in a
     * few seconds. Reconnecting on each would tear the tunnel down repeatedly
     * and land on whichever network happened to be first, not the one the
     * device settled on.
     */
    @Test
    fun `a flap between networks collapses into one reconnect`() = runTest {
        var reconnects = 0
        val monitor = monitor(TestScope(StandardTestDispatcher(testScheduler))) { reconnects++ }

        monitor.report(WIFI)
        monitor.report(CELLULAR)
        advanceTimeBy(500)
        monitor.report(OTHER_WIFI)
        advanceTimeBy(500)
        monitor.report(CELLULAR)
        advanceUntilIdle()

        assertEquals(1, reconnects)
    }

    /**
     * The user stopping the worker while the device is mid-handoff must not be
     * followed by a reconnect a few seconds later.
     */
    @Test
    fun `stopping cancels a reconnect that has not fired yet`() = runTest {
        var reconnects = 0
        val monitor = monitor(TestScope(StandardTestDispatcher(testScheduler))) { reconnects++ }

        monitor.report(WIFI)
        monitor.report(CELLULAR)
        advanceTimeBy(500)
        monitor.cancelPending()
        advanceUntilIdle()

        assertEquals(0, reconnects)
    }

    private fun monitor(scope: TestScope, onChanged: () -> Unit) =
        NetworkChangeMonitor(scope = scope, debounceMs = DEBOUNCE_MS, onChanged = onChanged)
}
