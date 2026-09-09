package com.meerkly.android.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Decides when a change of default network is worth reconnecting for.
 *
 * Deliberately knows nothing about Android: it takes network *identities* as
 * opaque numbers and reports a settled change. [DefaultNetworkWatcher] is the
 * piece that talks to `ConnectivityManager`. Splitting them is what makes this
 * testable off a device, which matters because every rule below exists to stop
 * a reconnect, and a rule that silently stops all of them looks identical to a
 * quiet phone.
 *
 * Why reconnect at all: a QUIC connection survives its device changing network.
 * The tunnel migrates onto the new path and keeps carrying traffic, so no
 * handshake happens and the client never re-announces where it now leaves from.
 * The gateway notices this on its own (see `egress_watch` there); this is the
 * client saying it too.
 *
 * @param debounceMs how long the device must stay on one network before the
 *   change counts. Walking out of wifi range produces several changes in a few
 *   seconds, and reconnecting on each would tear the tunnel down repeatedly and
 *   settle on whichever network came first rather than the one that lasted.
 *   Trailing edge, so the *last* network in a flap is the one that wins.
 */
internal class NetworkChangeMonitor(
    private val scope: CoroutineScope,
    private val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
    private val onChanged: () -> Unit,
) {
    private var seen: Long? = null
    private var pending: Job? = null

    /**
     * Note the network now carrying traffic.
     *
     * The first one is never a change: `registerDefaultNetworkCallback`
     * delivers whatever is already in use the moment it is registered, and
     * treating that as news would drop a healthy tunnel every time the worker
     * starts. Nor is the same one twice, which the framework delivers whenever
     * a network's capabilities or link properties move.
     */
    fun report(network: Long) {
        val previous = seen
        seen = network
        if (previous == null || previous == network) return

        pending?.cancel()
        pending = scope.launch {
            delay(debounceMs)
            onChanged()
        }
    }

    /** Drop a reconnect that has not fired yet. */
    fun cancelPending() {
        pending?.cancel()
        pending = null
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MS = 3_000L
    }
}
