package com.meerkly.android.ui.nav

import androidx.annotation.StringRes
import com.meerkly.android.R

/**
 * The app's three tabs.
 *
 * Activity was a fourth until 2.0.0. It showed pages this device had fetched,
 * and the proxy SDK has no notion of a job to list — the honest options were a
 * chart the web dashboard draws better, or a connection log with one line a
 * week on a healthy device.
 *
 * [key] is the stable string used for rotation state and for the debug
 * start-destination intent extra the screenshot script drives — never the enum
 * ordinal, which would silently repoint if the order ever changed.
 */
enum class Destination(val key: String, @StringRes val label: Int) {
    Home("home", R.string.nav_home),
    Devices("devices", R.string.nav_devices),
    Settings("settings", R.string.nav_settings),
    ;

    companion object {
        /** Null for an unknown or missing key — callers fall back to [Home]. */
        fun fromKey(key: String?): Destination? = entries.firstOrNull { it.key == key }
    }
}
