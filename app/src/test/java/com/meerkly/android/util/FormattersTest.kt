package com.meerkly.android.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FormattersTest {

    @Test
    fun `bytes step through decimal units`() {
        assertEquals("512 B", Formatters.bytes(512, Locale.US))
        assertEquals("2 KB", Formatters.bytes(2_000, Locale.US))
        assertEquals("1.5 MB", Formatters.bytes(1_500_000, Locale.US))
        assertEquals("2.50 GB", Formatters.bytes(2_500_000_000, Locale.US))
    }

    @Test
    fun `usd always shows cents`() {
        assertEquals("$0.00", Formatters.usd(0.0))
        assertEquals("$0.20", Formatters.usd(0.2))
        assertEquals("$12.30", Formatters.usd(12.3))
    }

    @Test
    fun `usd stays US-formatted even when the default locale is not`() {
        // Not locale-dependent by design: "$0,20" reads as a typo.
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("$0.20", Formatters.usd(0.2))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `durations switch from ms to seconds at a second`() {
        assertEquals("820 ms", Formatters.duration(820, Locale.US))
        assertEquals("1.0 s", Formatters.duration(1_000, Locale.US))
        assertEquals("30.0 s", Formatters.duration(30_000, Locale.US))
    }
}
