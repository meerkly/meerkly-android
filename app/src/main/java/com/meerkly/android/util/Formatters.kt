package com.meerkly.android.util

import java.util.Locale

/**
 * Display formatting for numbers the user sees.
 *
 * Every function takes an explicit [Locale] rather than letting `String.format`
 * reach for the default. That started as a testability fix — an implicit
 * default makes assertions pass in en-US and fail in de-DE — but [usd] genuinely
 * wants a *fixed* locale, which the implicit version hid.
 */
object Formatters {

    /** Stands in for a balance we don't have. An em dash, never "0" — see EarningsState. */
    const val UNKNOWN_VALUE = "—"

    /**
     * "$0.20" — an amount owed.
     *
     * Pinned to US formatting because the "$" is hard-coded: a dollar sign
     * paired with a comma decimal separator ("$0,02") reads as a typo. If
     * Meerkly ever shows other currencies this becomes a real currency format.
     */
    fun usd(amount: Double): String = String.format(Locale.US, "$%.2f", amount)

    /**
     * "1.5 GB" — traffic shared.
     *
     * Decimal units, not binary: this number sits beside money priced per GB,
     * and the server's GB is 1,000,000,000 bytes. A GiB here would make the
     * user's own arithmetic fail to reconcile.
     */
    fun bytes(bytes: Long, locale: Locale = Locale.getDefault()): String = when {
        bytes < 1_000 -> String.format(locale, "%d B", bytes)
        bytes < 1_000_000 -> String.format(locale, "%.0f KB", bytes / 1_000.0)
        bytes < 1_000_000_000 -> String.format(locale, "%.1f MB", bytes / 1_000_000.0)
        else -> String.format(locale, "%.2f GB", bytes / 1_000_000_000.0)
    }

    /** "820 ms" / "3.4 s" — how long a crawl took. */
    fun duration(millis: Long, locale: Locale = Locale.getDefault()): String =
        if (millis < 1_000) String.format(locale, "%d ms", millis)
        else String.format(locale, "%.1f s", millis / 1_000.0)
}
