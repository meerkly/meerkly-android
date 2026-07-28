package com.meerkly.android.util

import java.net.URI

/**
 * Port of the desktop `src/shared/urlValidator.ts`. Pure JVM (java.net.URI) so it is unit-testable
 * without Robolectric.
 *
 * Rules:
 *  - trim; reject empty
 *  - reject forbidden protocols: file, chrome, about, content, javascript, data
 *  - prepend `https://` when no protocol is present
 *  - allow only http / https
 *  - with [validateAndNormalize]'s `blockPrivateHosts`: reject loopback/private/link-local targets
 *    (used for gateway-dispatched fetch jobs, which must not probe the worker's own network;
 *    hostnames are not DNS-resolved, so rebinding is out of scope — same as desktop)
 *
 * Scheme detection is by the `scheme:` prefix (RFC 3986), not by `://`, so scheme-only URLs such as
 * `javascript:alert(1)` and `data:...` (which contain no `//`) are still caught. A bare host with a
 * port such as `example.com:8080` is treated as host:port, not a scheme.
 */
object UrlValidator {
    private val ALLOWED = setOf("http", "https")
    private val FORBIDDEN = setOf("file", "chrome", "about", "content", "javascript", "data")
    private val SCHEME_RE = Regex("^([a-zA-Z][a-zA-Z0-9+.\\-]*):")

    class ValidationException(message: String) : IllegalArgumentException(message)

    fun validateAndNormalize(input: String, blockPrivateHosts: Boolean = false): Result<String> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return fail("URL is empty")

        val rawScheme = SCHEME_RE.find(trimmed)?.groupValues?.get(1)?.lowercase()
        val normalized: String = if (rawScheme != null && looksLikeScheme(trimmed, rawScheme)) {
            when (rawScheme) {
                in FORBIDDEN -> return fail("Forbidden protocol: $rawScheme")
                in ALLOWED -> trimmed
                else -> return fail("Unsupported protocol: $rawScheme")
            }
        } else {
            "https://$trimmed"
        }

        val uri = try {
            URI(normalized)
        } catch (_: Exception) {
            return fail("Invalid URL")
        }

        val scheme = uri.scheme?.lowercase() ?: return fail("Invalid URL")
        if (scheme !in ALLOWED) {
            return if (scheme in FORBIDDEN) fail("Forbidden protocol: $scheme")
            else fail("Unsupported protocol: $scheme")
        }
        val host = uri.host
        if (host.isNullOrEmpty()) return fail("URL has no host")

        if (blockPrivateHosts && isPrivateHost(host)) {
            return fail("Private, loopback, and link-local addresses are not allowed")
        }

        return Result.success(uri.toString())
    }

    /**
     * True for hosts that point into the local machine or private network. Mirrors the desktop's
     * `isPrivateHostname` (urlValidator.ts): localhost and `*.localhost`; IPv4 0/8, 10/8, 127/8,
     * 100.64/10, 169.254/16, 172.16/12, 192.168/16; IPv6 ::, ::1, fe80::/10, fc00::/7, and
     * IPv4-mapped (::ffff:x). The desktop checks the WHATWG-normalized hostname, which folds
     * decimal/hex/octal IPv4 forms (2130706433, 0x7f000001, 127.1) to dotted-quad — java.net.URI
     * does not, and GeckoView's WHATWG parser WOULD resolve them, so [parseIpv4] does that
     * normalization here.
     */
    private fun isPrivateHost(hostname: String): Boolean {
        val host = hostname.lowercase()

        if (host == "localhost" || host.endsWith(".localhost")) return true

        // IPv6 literal — URI.getHost() keeps the brackets.
        if (host.startsWith("[") && host.endsWith("]")) {
            val ip = host.substring(1, host.length - 1)
            if (ip == "::" || ip == "::1") return true // unspecified / loopback
            if (ip.length >= 3 && ip.startsWith("fe") && ip[2] in "89ab") return true // fe80::/10
            if (ip.length >= 2 && ip[0] == 'f' && ip[1] in "cd") return true // unique-local fc00::/7
            if (ip.startsWith("::ffff:")) return isPrivateHost(ip.substring(7)) // IPv4-mapped
            return false
        }

        val addr = parseIpv4(host) ?: return false
        val a = (addr shr 24 and 0xff).toInt()
        val b = (addr shr 16 and 0xff).toInt()
        return a == 0 || // "this network" (0.0.0.0/8)
            a == 10 || // 10/8
            a == 127 || // loopback
            (a == 100 && b in 64..127) || // CGNAT 100.64/10
            (a == 169 && b == 254) || // link-local (incl. cloud metadata)
            (a == 172 && b in 16..31) || // 172.16/12
            (a == 192 && b == 168) // 192.168/16
    }

    /**
     * WHATWG-style IPv4 host parsing: up to four dot-separated parts (one trailing dot allowed),
     * each decimal, `0x` hex, or leading-zero octal; the last part fills the remaining bytes.
     * Returns the 32-bit address, or null when the host is not an IPv4 literal (a domain name).
     */
    private fun parseIpv4(host: String): Long? {
        var parts = host.split('.')
        if (parts.size > 1 && parts.last().isEmpty()) parts = parts.dropLast(1)
        if (parts.isEmpty() || parts.size > 4) return null
        val nums = parts.map { parseIpv4Part(it) ?: return null }
        for (i in 0 until nums.size - 1) if (nums[i] > 0xff) return null
        if (nums.last() >= 1L shl (8 * (5 - nums.size))) return null
        var value = nums.last()
        for (i in 0 until nums.size - 1) value += nums[i] shl (8 * (3 - i))
        return value
    }

    private fun parseIpv4Part(part: String): Long? {
        var digits = part
        var radix = 10
        if (digits.length >= 2 && (digits.startsWith("0x") || digits.startsWith("0X"))) {
            digits = digits.substring(2)
            radix = 16
        } else if (digits.length >= 2 && digits[0] == '0') {
            digits = digits.substring(1)
            radix = 8
        }
        if (part.isEmpty()) return null
        if (digits.isEmpty()) return 0L // bare "0x" is 0 per the WHATWG spec
        return digits.toLongOrNull(radix)?.takeIf { it >= 0 }
    }

    /**
     * True when [scheme] is a real URL scheme rather than the host part of a `host:port` input.
     * Known allowed/forbidden schemes always count. An unknown "scheme" immediately followed by a
     * digit and no `//` (e.g. `example.com:8080`) is treated as host:port, not a scheme.
     */
    private fun looksLikeScheme(input: String, scheme: String): Boolean {
        if (scheme in ALLOWED || scheme in FORBIDDEN) return true
        if (input.startsWith("$scheme://", ignoreCase = true)) return true
        val afterColon = input.getOrNull(scheme.length + 1)
        return afterColon?.isDigit() != true
    }

    private fun fail(message: String): Result<String> = Result.failure(ValidationException(message))
}
