package com.meerkly.android.diagnostics

import com.meerkly.android.util.MiniJson

/** Metadata gathered for a diagnostics export. Plain data so assembly is unit-testable. */
data class DiagnosticsMeta(
    val machineId: String,
    val appVersion: String,
    val androidSdkInt: Int,
    val deviceModel: String,
    val locale: String,
    val timezone: String,
    val geckoViewVersion: String?,
    val profileStatus: String,
    val latestNavigation: Map<String, Any?>?,
    val logRetention: Map<String, Any?>,
    val generatedAt: String,
)

/** Builds the canonical `diagnostics.json` payload from [DiagnosticsMeta]. */
object DiagnosticsJson {
    fun build(meta: DiagnosticsMeta): String = MiniJson.encode(
        linkedMapOf(
            "machine_id" to meta.machineId,
            "app_version" to meta.appVersion,
            "android_sdk_int" to meta.androidSdkInt,
            "device_model" to meta.deviceModel,
            "locale" to meta.locale,
            "timezone" to meta.timezone,
            "geckoview_version" to meta.geckoViewVersion,
            "profile_status" to meta.profileStatus,
            "latest_navigation" to meta.latestNavigation,
            "log_retention" to meta.logRetention,
            "generated_at" to meta.generatedAt,
        )
    )
}
