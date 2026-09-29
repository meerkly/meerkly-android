package com.meerkly.android.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
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
 * It also reports the default network's transport ([NetworkTransport]) on every
 * capabilities change, and null when the default network is lost, which the
 * gateway uses to class the exit as mobile, residential or datacenter.
 *
 * Requires `ACCESS_NETWORK_STATE`, which this app already holds.
 */
internal class DefaultNetworkWatcher(
    context: Context,
    private val logger: AppLogger,
    private val monitor: NetworkChangeMonitor,
    private val onTransport: (String?) -> Unit = {},
) {
    private val connectivity =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            monitor.report(network.networkHandle)
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            onTransport(transportOf(caps))
        }

        override fun onLost(network: Network) {
            onTransport(null)
        }
    }

    /**
     * The transport of the network in use right now, or null with none. Read
     * synchronously at start so the first handshake carries it; the callback
     * above only runs while registered.
     */
    fun currentTransport(): String? = runCatching {
        val cm = connectivity ?: return null
        val active = cm.activeNetwork ?: return null
        cm.getNetworkCapabilities(active)?.let(::transportOf)
    }.getOrNull()

    private fun transportOf(caps: NetworkCapabilities): String = NetworkTransport.of(
        cellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
        wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
        ethernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
    )

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
