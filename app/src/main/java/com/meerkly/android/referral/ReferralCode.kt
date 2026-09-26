package com.meerkly.android.referral

import java.net.URLDecoder

/**
 * Pure parsing for the invite code a meerkly.com/r/CODE link carries into the
 * Play Store as `referrer=ref%3DCODE…`. Kept free of Android types so it is a
 * plain JVM unit test.
 */
object ReferralCode {
    private val VALID = Regex("^[A-Z2-9]{8}$")

    /** Is [code] shaped like a server-issued referral code? */
    fun isValid(code: String?): Boolean = code != null && VALID.matches(code)

    /**
     * The `ref` value from an install-referrer string such as
     * `ref=ABCDEFGH&utm_source=meerkly`, or null when it has none or it is not
     * a valid code. Tolerates the whole string arriving URL-encoded once more
     * (`ref%3DABCDEFGH%26…`), which some link shorteners and browsers do.
     */
    fun fromInstallReferrer(referrer: String?): String? {
        if (referrer.isNullOrBlank()) return null
        refParam(referrer)?.let { return it }
        // No usable ref= at the top level: maybe the whole thing is encoded.
        val decoded = decode(referrer) ?: return null
        return if (decoded != referrer) refParam(decoded) else null
    }

    private fun refParam(query: String): String? =
        query.trim().removePrefix("?").split('&').firstNotNullOfOrNull { pair ->
            val eq = pair.indexOf('=')
            if (eq <= 0) return@firstNotNullOfOrNull null
            val key = decode(pair.substring(0, eq))?.trim()
            if (key != "ref") return@firstNotNullOfOrNull null
            decode(pair.substring(eq + 1))?.trim()?.uppercase()?.takeIf(::isValid)
        }

    private fun decode(s: String): String? =
        runCatching { URLDecoder.decode(s, Charsets.UTF_8.name()) }.getOrNull()
}
