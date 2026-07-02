package com.meerkly.android.data

import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * Self-reported device facts, used verbatim in BOTH places this device
 * describes itself: the gateway register frame (GatewayClient.buildRegister)
 * and account device registration (DeviceRegistrationManager). One source so
 * they can't drift — mirrors the desktop's deviceInfo.ts.
 */
data class DeviceInfo(
    val name: String,
    val deviceModel: String,
    val os: String,
    val arch: String,
    val appVersion: String,
    val engineVersion: String,
) {
    companion object {
        fun collect(context: Context, geckoVersion: String?): DeviceInfo {
            val appVersion = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: "?"
            // The user-visible device name ("Rasmus's Pixel"), like the desktop's
            // hostname; falls back to the model for devices that never set one.
            val name = runCatching {
                Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: Build.MODEL
            return DeviceInfo(
                name = name,
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                os = "android ${Build.VERSION.RELEASE} (sdk ${Build.VERSION.SDK_INT})",
                arch = Build.SUPPORTED_ABIS.firstOrNull() ?: "",
                appVersion = appVersion,
                engineVersion = "GeckoView ${geckoVersion ?: "unknown"}",
            )
        }
    }
}
