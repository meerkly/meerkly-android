package com.meerkly.android.model

/** A single structured log record, serialized one-per-line as JSONL. */
data class LogEntry(
    val ts: String,
    val level: String,
    val event: String,
    val machineId: String,
    val data: Map<String, Any?>,
)
