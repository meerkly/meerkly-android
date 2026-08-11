package com.meerkly.android.ui.nav

import androidx.annotation.StringRes
import com.meerkly.android.R

/**
 * The app's four tabs.
 *
 * [key] is the stable string used for rotation state and for the debug
 * start-destination intent extra the screenshot script drives — never the enum
 * ordinal, which would silently repoint if the order ever changed.
 */
enum class Destination(val key: String, @StringRes val label: Int) {
    Home("home", R.string.nav_home),
    Activity("activity", R.string.nav_activity),
    Devices("devices", R.string.nav_devices),
    Settings("settings", R.string.nav_settings),
    ;

    companion object {
        /** Null for an unknown or missing key — callers fall back to [Home]. */
        fun fromKey(key: String?): Destination? = entries.firstOrNull { it.key == key }
    }
}
