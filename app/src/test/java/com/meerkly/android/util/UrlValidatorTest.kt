package com.meerkly.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlValidatorTest {

    private fun ok(input: String) = UrlValidator.validateAndNormalize(input).getOrNull()
    private fun err(input: String) = UrlValidator.validateAndNormalize(input).exceptionOrNull()

    @Test
    fun rejectsEmptyAndBlank() {
        assertNull(ok(""))
        assertNull(ok("   "))
    }

    @Test
    fun passesThroughHttpAndHttps() {
        assertEquals("https://example.com", ok("https://example.com"))
        assertEquals("http://foo.com/bar", ok("http://foo.com/bar"))
    }

    @Test
    fun prependsHttpsWhenNoScheme() {
        assertEquals("https://example.com", ok("example.com"))
    }

    @Test
    fun trimsWhitespace() {
        assertEquals("https://trim.me", ok("  https://trim.me  "))
    }

    @Test
    fun treatsHostPortAsHostNotScheme() {
        assertEquals("https://example.com:8080", ok("example.com:8080"))
        assertEquals("https://localhost:3000", ok("localhost:3000"))
    }

    @Test
    fun rejectsForbiddenSchemes() {
        for (bad in listOf(
            "javascript:alert(1)",
            "data:text/html,<h1>x</h1>",
            "file:///etc/passwd",
            "about:blank",
            "chrome://settings",
            "content://media/external/images",
        )) {
            val e = err(bad)
            assertTrue("expected $bad to be rejected", e != null)
            assertTrue("expected forbidden message for $bad: ${e?.message}", e!!.message!!.contains("Forbidden"))
        }
    }

    @Test
    fun rejectsUnsupportedSchemes() {
        val e = err("ftp://server/file")
        assertTrue(e != null && e.message!!.contains("Unsupported"))
    }
}
