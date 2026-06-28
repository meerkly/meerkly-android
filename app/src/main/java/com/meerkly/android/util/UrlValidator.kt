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

    fun validateAndNormalize(input: String): Result<String> {
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
        if (uri.host.isNullOrEmpty()) return fail("URL has no host")

        return Result.success(uri.toString())
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
