package com.meerkly.android.logging

import com.meerkly.android.model.LogEntry
import kotlinx.coroutines.flow.StateFlow

/** Structured logging facade. Implementations write JSONL and expose recent entries for the UI. */
interface AppLogger {
    fun info(event: String, data: Map<String, Any?> = emptyMap())
    fun warn(event: String, data: Map<String, Any?> = emptyMap())
    fun error(event: String, data: Map<String, Any?> = emptyMap())

    /** Most-recent entries (oldest first), capped, for the in-app log panel. */
    val recentEntries: StateFlow<List<LogEntry>>
}
