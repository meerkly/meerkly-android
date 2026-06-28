package com.meerkly.android.logging

import com.meerkly.android.model.LogEntry
import com.meerkly.android.util.MiniJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Appends one JSON object per line to a per-day file under [logDir]
 * (`meerkly-YYYY-MM-DD.jsonl`, UTC date) and keeps the last [maxRecent] entries in memory for the
 * UI. Decoupled from Android Context (takes a plain [File]) so it is unit-testable on the JVM.
 */
class JsonlFileLogger(
    private val logDir: File,
    private val machineId: String,
    private val maxRecent: Int = 200,
    private val clock: () -> Instant = { Instant.now() },
) : AppLogger {

    private val _recentEntries = MutableStateFlow<List<LogEntry>>(emptyList())
    override val recentEntries: StateFlow<List<LogEntry>> = _recentEntries.asStateFlow()

    init {
        logDir.mkdirs()
    }

    override fun info(event: String, data: Map<String, Any?>) = log("info", event, data)
    override fun warn(event: String, data: Map<String, Any?>) = log("warn", event, data)
    override fun error(event: String, data: Map<String, Any?>) = log("error", event, data)

    @Synchronized
    private fun log(level: String, event: String, data: Map<String, Any?>) {
        val now = clock()
        val entry = LogEntry(
            ts = DateTimeFormatter.ISO_INSTANT.format(now),
            level = level,
            event = event,
            machineId = machineId,
            data = data,
        )
        append(now, entry)
        _recentEntries.value = (_recentEntries.value + entry).takeLast(maxRecent)
    }

    private fun append(now: Instant, entry: LogEntry) {
        val date = now.atZone(ZoneOffset.UTC).toLocalDate()
        val file = File(logDir, "meerkly-$date.jsonl")
        val obj = linkedMapOf<String, Any?>(
            "ts" to entry.ts,
            "level" to entry.level,
            "event" to entry.event,
            "machine_id" to entry.machineId,
            "data" to entry.data,
        )
        runCatching {
            file.appendText(MiniJson.encode(obj) + "\n")
        }
    }

    fun directory(): File = logDir
}
