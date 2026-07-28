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

    // --- blockPrivateHosts (SSRF guard for gateway-dispatched fetch jobs) ---

    private fun okBlocked(input: String) =
        UrlValidator.validateAndNormalize(input, blockPrivateHosts = true).getOrNull()

    private fun errBlocked(input: String) =
        UrlValidator.validateAndNormalize(input, blockPrivateHosts = true).exceptionOrNull()

    @Test
    fun defaultModeStillAllowsPrivateHosts() {
        assertEquals("https://localhost:3000", ok("localhost:3000"))
        assertEquals("https://127.0.0.1", ok("127.0.0.1"))
        assertEquals("https://192.168.1.1", ok("192.168.1.1"))
    }

    @Test
    fun blockPrivateHostsRejectsLocalhostAndLoopback() {
        for (bad in listOf(
            "http://localhost",
            "https://LOCALHOST:3000",
            "http://foo.localhost/admin",
            "http://127.0.0.1",
            "http://127.8.9.10:8080/path",
        )) {
            val e = errBlocked(bad)
            assertTrue("expected $bad to be rejected", e != null)
            assertTrue("expected private-host message for $bad: ${e?.message}", e!!.message!!.contains("Private"))
        }
    }

    @Test
    fun blockPrivateHostsRejectsPrivateAndSpecialIpv4Ranges() {
        for (bad in listOf(
            "http://0.0.0.0",          // "this network" 0/8
            "http://10.0.0.1",         // 10/8
            "http://100.64.0.1",       // CGNAT 100.64/10
            "http://100.127.255.255",  // CGNAT upper bound
            "http://169.254.169.254",  // link-local / cloud metadata
            "http://172.16.0.1",       // 172.16/12 lower bound
            "http://172.31.255.255",   // 172.16/12 upper bound
            "http://192.168.1.1",      // 192.168/16
        )) {
            assertNull("expected $bad to be rejected", okBlocked(bad))
        }
    }

    @Test
    fun blockPrivateHostsRejectsIpv6PrivateLiterals() {
        for (bad in listOf(
            "http://[::1]",              // loopback
            "http://[::]",               // unspecified
            "http://[fe80::1]",          // link-local fe80::/10
            "http://[FEB0::1]",          // link-local upper range, case-insensitive
            "http://[fc00::1]",          // unique-local fc00::/7
            "http://[fd12:3456::1]",     // unique-local
            "http://[::ffff:127.0.0.1]", // IPv4-mapped loopback
            "http://[::ffff:10.0.0.1]",  // IPv4-mapped private
        )) {
            assertNull("expected $bad to be rejected", okBlocked(bad))
        }
    }

    @Test
    fun blockPrivateHostsRejectsNonDottedIpv4Forms() {
        // GeckoView's WHATWG URL parser resolves these to 127.0.0.1, so the
        // validator must too (the desktop gets this for free from `new URL`).
        for (bad in listOf(
            "http://2130706433",  // decimal
            "http://0x7f000001",  // hex
            "http://0177.0.0.1",  // octal first octet
            "http://127.1",       // two-part shorthand
        )) {
            assertNull("expected $bad to be rejected", okBlocked(bad))
        }
    }

    @Test
    fun blockPrivateHostsAllowsPublicHosts() {
        assertEquals("https://example.com", okBlocked("example.com"))
        assertEquals("http://8.8.8.8/probe", okBlocked("http://8.8.8.8/probe"))
        for (public in listOf(
            "http://9.255.255.255",   // just below 10/8
            "http://11.0.0.1",        // just above 10/8
            "http://100.63.255.255",  // just below CGNAT
            "http://100.128.0.1",     // just above CGNAT
            "http://172.15.0.1",      // just below 172.16/12
            "http://172.32.0.1",      // just above 172.16/12
            "http://192.169.0.1",     // just above 192.168/16
            "http://169.253.0.1",     // just below link-local
            "http://[2001:db8::1]",   // public-form IPv6
            "http://notlocalhost.com",
        )) {
            assertTrue("expected $public to be allowed", okBlocked(public) != null)
        }
    }
}
