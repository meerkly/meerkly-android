package com.meerkly.android.diagnostics

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.meerkly.android.logging.AppLogger
import com.meerkly.android.logging.LogRetention
import com.meerkly.android.proxy.ProxyState
import com.meerkly.android.util.MiniJson
import com.meerkly.android.util.ZipUtils
import java.io.File
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

/**
 * Builds a diagnostics ZIP (diagnostics.json, machine.json, app.json, proxy_status.json,
 * and the logs/ jsonl files) under cacheDir/diagnostics and shares it via FileProvider.
 * No page content and no publisher id are ever included.
 */
class DiagnosticsExporter(
    private val context: Context,
    private val logger: AppLogger,
    private val machineId: String,
    private val appVersion: String,
    private val retentionPolicy: LogRetention.Policy = LogRetention.Policy(),
) {
    fun export(proxyState: ProxyState, clientKey: String?): File {
        val now = Instant.now()
        val stamp = now.toString().replace(":", "-")
        val outDir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        val staging = File(outDir, "staging-$stamp").apply { mkdirs() }

        val meta = DiagnosticsMeta(
            machineId = machineId,
            appVersion = appVersion,
            androidSdkInt = Build.VERSION.SDK_INT,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            locale = Locale.getDefault().toLanguageTag(),
            timezone = TimeZone.getDefault().id,
            proxyState = proxyState.name,
            clientKey = clientKey,
            logRetention = linkedMapOf(
                "keep_days" to retentionPolicy.keepDays,
                "max_total_bytes" to retentionPolicy.maxTotalBytes,
            ),
            generatedAt = now.toString(),
        )

        File(staging, "diagnostics.json").writeText(DiagnosticsJson.build(meta))
        File(staging, "machine.json").writeText(MiniJson.encode(mapOf("machine_id" to machineId)))
        File(staging, "app.json").writeText(
            MiniJson.encode(
                linkedMapOf(
                    "app_version" to appVersion,
                    "package" to context.packageName,
                    "android_sdk_int" to Build.VERSION.SDK_INT,
                )
            )
        )
        File(staging, "proxy_status.json").writeText(
            MiniJson.encode(linkedMapOf("state" to proxyState.name, "client_key" to clientKey)),
        )

        val entries = mutableListOf<Pair<String, File>>()
        staging.listFiles()?.forEach { entries.add(it.name to it) }
        File(context.filesDir, "logs").listFiles { f -> f.name.endsWith(".jsonl") }
            ?.forEach { entries.add("logs/${it.name}" to it) }

        val zip = File(outDir, "meerkly-diagnostics-$stamp.zip")
        ZipUtils.zip(entries, zip)
        staging.deleteRecursively()
        logger.info("diagnostics.exported", mapOf("zip" to zip.name, "entries" to entries.size))
        return zip
    }

    companion object {
        fun shareIntent(context: Context, zip: File): Intent {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zip)
            return Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }
}
