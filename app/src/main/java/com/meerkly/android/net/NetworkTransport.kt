package com.meerkly.android.net

/**
 * The transport this device's traffic leaves over, in the words the SDK sends
 * the gateway: "cellular", "wifi", "ethernet" or "other".
 *
 * The gateway combines it with the network it measures the exit on to call the
 * exit mobile, residential or datacenter, which is what proxy customers select
 * by. Knows nothing about Android so the mapping is testable off a device;
 * [DefaultNetworkWatcher] reads the flags from `NetworkCapabilities`.
 *
 * A VPN's capabilities carry the transports of the networks under it, so a VPN
 * over mobile data still reads as cellular. When more than one is present
 * (a VPN over several networks) there is no telling which one carries the
 * traffic, and claiming cellular would be a guess that makes the exit look
 * mobile; that case is "other".
 */
internal object NetworkTransport {
    const val CELLULAR = "cellular"
    const val WIFI = "wifi"
    const val ETHERNET = "ethernet"
    const val OTHER = "other"

    fun of(cellular: Boolean, wifi: Boolean, ethernet: Boolean): String {
        val present = listOfNotNull(
            CELLULAR.takeIf { cellular },
            WIFI.takeIf { wifi },
            ETHERNET.takeIf { ethernet },
        )
        return present.singleOrNull() ?: OTHER
    }
}
