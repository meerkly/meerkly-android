package com.meerkly.android.util

import com.meerkly.android.model.Credits
import java.util.Locale

/**
 * Display formatting for numbers the user sees.
 *
 * Every function takes an explicit [Locale] rather than letting `String.format`
 * reach for the default. That started as a testability fix — an implicit
 * default makes assertions pass in en-US and fail in de-DE — but the two cases
 * below genuinely want *different* locales, which the implicit version hid.
 */
object Formatters {

    /** Stands in for a balance we don't have. An em dash, never "0" — see CreditsState. */
    const val UNKNOWN_VALUE = "—"

    /**
     * "12,300 credits". Grouping follows the user's locale on purpose: a German
     * reader expects 12.300, and credits are a plain count with no currency
     * attached.
     */
    fun credits(credits: Long, locale: Locale = Locale.getDefault()): String =
        String.format(locale, "%,d credits", credits)

    /**
     * "≈ $0.02" — the dollar hint under a credit balance (500,000 credits = $1).
     *
     * Pinned to US formatting because the "$" is hard-coded: pairing a dollar
     * sign with a comma decimal separator ("$0,02") reads as a typo. If Meerkly
     * ever shows other currencies this should become a real currency format.
     */
    fun dollars(credits: Long): String =
        String.format(Locale.US, "≈ $%.2f", credits / Credits.CREDITS_PER_DOLLAR)

    /** "1.2 MB" — page sizes in the activity feed. */
    fun bytes(bytes: Long, locale: Locale = Locale.getDefault()): String = when {
        bytes < 1_024 -> String.format(locale, "%d B", bytes)
        bytes < 1_024 * 1_024 -> String.format(locale, "%.0f KB", bytes / 1_024.0)
        else -> String.format(locale, "%.1f MB", bytes / (1_024.0 * 1_024.0))
    }

    /** "820 ms" / "3.4 s" — how long a crawl took. */
    fun duration(millis: Long, locale: Locale = Locale.getDefault()): String =
        if (millis < 1_000) String.format(locale, "%d ms", millis)
        else String.format(locale, "%.1f s", millis / 1_000.0)
}
