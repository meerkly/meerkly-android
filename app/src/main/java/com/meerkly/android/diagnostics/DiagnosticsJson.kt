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
    val proxyState: String,
    val clientKey: String?,
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
            "proxy_state" to meta.proxyState,
            "client_key" to meta.clientKey,
            "log_retention" to meta.logRetention,
            "generated_at" to meta.generatedAt,
        )
    )
}
