package com.meerkly.android.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

class JsonlFileLoggerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun writesOneJsonlLinePerEvent() {
        val dir = tmp.newFolder("logs")
        val fixed = Instant.parse("2026-06-28T14:22:00Z")
        val logger = JsonlFileLogger(dir, machineId = "machine_abc", clock = { fixed })

        logger.info("browser.navigation_completed", mapOf("duration_ms" to 3510))

        val file = File(dir, "meerkly-2026-06-28.jsonl")
        assertTrue("expected per-day file to exist", file.exists())
        val lines = file.readText().trim().split("\n")
        assertEquals(1, lines.size)
        val line = lines.first()
        assertTrue(line.contains("\"ts\":\"2026-06-28T14:22:00Z\""))
        assertTrue(line.contains("\"level\":\"info\""))
        assertTrue(line.contains("\"event\":\"browser.navigation_completed\""))
        assertTrue(line.contains("\"machine_id\":\"machine_abc\""))
        assertTrue(line.contains("\"data\":{\"duration_ms\":3510}"))
    }

    @Test
    fun exposesRecentEntriesCappedAtMax() {
        val dir = tmp.newFolder("logs")
        val logger = JsonlFileLogger(dir, machineId = "m", maxRecent = 3)
        repeat(5) { logger.info("event.$it") }

        val recent = logger.recentEntries.value
        assertEquals(3, recent.size)
        // Oldest-first, capped: should be the last three events.
        assertEquals(listOf("event.2", "event.3", "event.4"), recent.map { it.event })
    }
}
