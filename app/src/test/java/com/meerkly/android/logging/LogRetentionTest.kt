package com.meerkly.android.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

class LogRetentionTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun writeLog(dir: File, date: String, bytes: Int) {
        File(dir, "meerkly-$date.jsonl").writeText("x".repeat(bytes))
    }

    @Test
    fun deletesFilesOlderThanKeepDays() {
        val dir = tmp.newFolder("logs")
        writeLog(dir, "2026-06-01", 10) // older than cutoff
        writeLog(dir, "2026-06-25", 10) // within window
        writeLog(dir, "2026-06-28", 10) // today

        val outcome = LogRetention.apply(
            dir,
            today = LocalDate.parse("2026-06-28"),
            policy = LogRetention.Policy(keepDays = 7, maxTotalBytes = Long.MAX_VALUE),
        )

        assertTrue("2026-06-01 should be deleted", outcome.deleted.contains("meerkly-2026-06-01.jsonl"))
        assertFalse(File(dir, "meerkly-2026-06-01.jsonl").exists())
        assertTrue(File(dir, "meerkly-2026-06-25.jsonl").exists())
        assertTrue(File(dir, "meerkly-2026-06-28.jsonl").exists())
    }

    @Test
    fun enforcesSizeCapDeletingOldestButKeepingToday() {
        val dir = tmp.newFolder("logs")
        // Each 1000 bytes; cap 2500 -> must drop oldest until <= 2500, never today's file.
        writeLog(dir, "2026-06-26", 1000)
        writeLog(dir, "2026-06-27", 1000)
        writeLog(dir, "2026-06-28", 1000) // today

        val outcome = LogRetention.apply(
            dir,
            today = LocalDate.parse("2026-06-28"),
            policy = LogRetention.Policy(keepDays = 30, maxTotalBytes = 2500),
        )

        assertTrue("oldest should be deleted", outcome.deleted.contains("meerkly-2026-06-26.jsonl"))
        assertFalse(File(dir, "meerkly-2026-06-26.jsonl").exists())
        assertTrue("today's file must survive", File(dir, "meerkly-2026-06-28.jsonl").exists())
        assertTrue("remaining must be under cap", outcome.remainingBytes <= 2500)
    }

    @Test
    fun emptyDirectoryIsSafe() {
        val dir = tmp.newFolder("logs")
        val outcome = LogRetention.apply(dir, LocalDate.parse("2026-06-28"))
        assertEquals(emptyList<String>(), outcome.deleted)
    }
}
