package com.meerkly.android.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FormattersTest {

    @Test
    fun `credits group by the reader's locale`() {
        assertEquals("12,300 credits", Formatters.credits(12_300, Locale.US))
        // Regression: this used to call "%,d".format() with the implicit default
        // locale, so the assertion above passed in en-US and failed in de-DE.
        assertEquals("12.300 credits", Formatters.credits(12_300, Locale.GERMANY))
    }

    @Test
    fun `dollars stay US-formatted because the dollar sign is hard-coded`() {
        // Not locale-dependent by design: "$0,02" reads as a typo.
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("≈ $0.02", Formatters.dollars(12_300))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `dollars round to cents`() {
        assertEquals("≈ $1.00", Formatters.dollars(500_000))
        assertEquals("≈ $0.00", Formatters.dollars(100))
        assertEquals("≈ $0.11", Formatters.dollars(57_000))
    }

    @Test
    fun `bytes step through the units`() {
        assertEquals("512 B", Formatters.bytes(512, Locale.US))
        assertEquals("2 KB", Formatters.bytes(2_048, Locale.US))
        assertEquals("1.5 MB", Formatters.bytes(1_572_864, Locale.US))
    }

    @Test
    fun `durations switch from ms to seconds at a second`() {
        assertEquals("820 ms", Formatters.duration(820, Locale.US))
        assertEquals("1.0 s", Formatters.duration(1_000, Locale.US))
        assertEquals("30.0 s", Formatters.duration(30_000, Locale.US))
    }
}
