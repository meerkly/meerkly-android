package com.meerkly.android.diagnostics

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.meerkly.android.data.RecentNavigationRepository
import com.meerkly.android.logging.AppLogger
import com.meerkly.android.logging.LogRetention
import com.meerkly.android.model.BrowserStatus
import com.meerkly.android.util.MiniJson
import com.meerkly.android.util.ZipUtils
import java.io.File
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

/**
 * Builds a diagnostics ZIP (diagnostics.json, machine.json, app.json, browser_status.json,
 * recent_navigations.json, and the logs/ jsonl files) under cacheDir/diagnostics and shares it via
 * FileProvider. Full page HTML is never included.
 */
class DiagnosticsExporter(
    private val context: Context,
    private val logger: AppLogger,
    private val recentRepo: RecentNavigationRepository,
    private val machineId: String,
    private val geckoViewVersion: String?,
    private val retentionPolicy: LogRetention.Policy = LogRetention.Policy(),
) {
    fun export(status: BrowserStatus): File {
        val now = Instant.now()
        val stamp = now.toString().replace(":", "-")
        val outDir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        val staging = File(outDir, "staging-$stamp").apply { mkdirs() }

        val meta = DiagnosticsMeta(
            machineId = machineId,
            appVersion = appVersion(),
            androidSdkInt = Build.VERSION.SDK_INT,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            locale = Locale.getDefault().toLanguageTag(),
            timezone = TimeZone.getDefault().id,
            geckoViewVersion = geckoViewVersion,
            profileStatus = "app-scoped default GeckoRuntime profile",
            latestNavigation = recentRepo.latest()?.toJsonMap(),
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
                    "app_version" to appVersion(),
                    "package" to context.packageName,
                    "android_sdk_int" to Build.VERSION.SDK_INT,
                    "geckoview_version" to geckoViewVersion,
                )
            )
        )
        File(staging, "browser_status.json").writeText(MiniJson.encode(statusToMap(status)))
        File(staging, "recent_navigations.json").writeText(MiniJson.encode(recentRepo.snapshotJsonMaps()))

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

    private fun appVersion(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    }.getOrDefault("unknown")

    private fun statusToMap(status: BrowserStatus): Map<String, Any?> = when (status) {
        is BrowserStatus.Idle -> mapOf("state" to "idle")
        is BrowserStatus.Loading -> linkedMapOf("state" to "loading", "requested_url" to status.requestedUrl)
        is BrowserStatus.Success -> linkedMapOf("state" to "success", "result" to status.result.toJsonMap())
        is BrowserStatus.Error -> linkedMapOf("state" to "error", "result" to status.result.toJsonMap())
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
