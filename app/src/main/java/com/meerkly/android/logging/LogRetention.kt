package com.meerkly.android.logging

import java.io.File
import java.time.LocalDate

/**
 * Pure retention logic over a directory of `meerkly-YYYY-MM-DD.jsonl` files:
 *  1. delete files whose date is older than [Policy.keepDays] before `today`
 *  2. if the remaining total still exceeds [Policy.maxTotalBytes], delete oldest first (never
 *     today's active file) until under the cap.
 * Operates on a plain [File] + [LocalDate] so it is unit-testable without Android.
 */
object LogRetention {

    data class Policy(
        val keepDays: Long = 7,
        val maxTotalBytes: Long = 5L * 1024 * 1024,
    )

    data class Outcome(val deleted: List<String>, val remainingBytes: Long)

    private val FILE_RE = Regex("""meerkly-(\d{4}-\d{2}-\d{2})\.jsonl""")

    fun apply(logDir: File, today: LocalDate, policy: Policy = Policy()): Outcome {
        if (!logDir.isDirectory) return Outcome(emptyList(), 0)

        val logs = logDir.listFiles { f -> FILE_RE.matches(f.name) }?.toMutableList() ?: mutableListOf()
        val deleted = mutableListOf<String>()

        val cutoff = today.minusDays(policy.keepDays)
        logs.removeAll { f ->
            val date = parseDate(f.name)
            if (date != null && date.isBefore(cutoff) && f.delete()) {
                deleted.add(f.name)
                true
            } else false
        }

        logs.sortBy { parseDate(it.name) } // oldest first
        var total = logs.sumOf { it.length() }
        var idx = 0
        while (total > policy.maxTotalBytes && idx < logs.size) {
            val f = logs[idx]
            if (parseDate(f.name) == today) { // keep today's active file
                idx++
                continue
            }
            val len = f.length()
            if (f.delete()) {
                deleted.add(f.name)
                total -= len
            }
            idx++
        }

        return Outcome(deleted, total)
    }

    private fun parseDate(name: String): LocalDate? =
        FILE_RE.find(name)?.groupValues?.get(1)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}
