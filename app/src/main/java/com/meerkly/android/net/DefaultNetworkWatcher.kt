package com.meerkly.android.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.meerkly.android.logging.AppLogger

/**
 * The `ConnectivityManager` half of watching for a network change.
 *
 * All the deciding lives in [NetworkChangeMonitor], which knows nothing about
 * Android and is unit-tested off a device. This class exists to turn framework
 * callbacks into the one number that decision needs: `Network.networkHandle`,
 * which identifies a network across callbacks where the `Network` object itself
 * is only equal by identity.
 *
 * Uses `registerDefaultNetworkCallback`, not a request with capabilities: the
 * question is which network the process's traffic is *actually* leaving over,
 * and that is what the default network means. A capability-filtered request
 * would also report networks the process is not using.
 *
 * Requires `ACCESS_NETWORK_STATE`, which this app already holds.
 */
internal class DefaultNetworkWatcher(
    context: Context,
    private val logger: AppLogger,
    private val monitor: NetworkChangeMonitor,
) {
    private val connectivity =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            monitor.report(network.networkHandle)
        }
    }

    private var registered = false

    fun start() {
        if (registered) return
        val cm = connectivity ?: return
        // Registration itself can throw on a device whose connectivity service
        // is in a bad way, and losing the reconnect is not worth losing the
        // worker: the gateway notices a migration on its own regardless.
        runCatching { cm.registerDefaultNetworkCallback(callback) }
            .onSuccess { registered = true }
            .onFailure { logger.warn("net.watch_failed", mapOf("error" to it.message)) }
    }

    fun stop() {
        if (!registered) return
        registered = false
        monitor.cancelPending()
        runCatching { connectivity?.unregisterNetworkCallback(callback) }
    }
}
