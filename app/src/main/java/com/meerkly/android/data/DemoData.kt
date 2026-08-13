package com.meerkly.android.data

import com.meerkly.android.model.NavigationResult
import java.time.Instant

/**
 * Placeholder crawl history for store screenshots, behind MainActivity's
 * debug-only `meerkly.demo` launch extra (scripts/screenshots.sh). The activity
 * ring is in-memory and deliberately starts empty on every launch, so a
 * cold-started app would otherwise always screenshot the empty state.
 *
 * The rows mirror what a worker actually produces — public sites, realistic
 * timings, and one honest timeout — and never touch real user data.
 */
object DemoData {

    /** Records the demo rows into [repo], newest last so the ring shows them newest-first. */
    fun seed(repo: RecentNavigationRepository, now: Instant = Instant.now()) {
        results(now).sortedBy { it.startedAt }.forEach(repo::record)
    }

    fun results(now: Instant): List<NavigationResult> {
        fun ok(host: String, path: String, title: String, ms: Long, minsAgo: Long, bytes: Long) =
            NavigationResult(
                success = true,
                requestedUrl = "https://$host$path",
                finalUrl = "https://$host$path",
                title = title,
                error = null,
                startedAt = now.minusSeconds(minsAgo * 60),
                finishedAt = now.minusSeconds(minsAgo * 60).plusMillis(ms),
                loadedMs = ms,
                htmlSizeBytes = bytes,
            )
        return listOf(
            ok("ahrefs.com", "/website-authority-checker", "Website Authority Checker", 780, 1, 812_956),
            ok("bbc.co.uk", "/news/technology", "BBC News — Technology", 1_240, 4, 1_204_331),
            ok("example.com", "/", "Example Domain", 210, 9, 1_256),
            NavigationResult(
                success = false,
                requestedUrl = "https://shop.zalando.se/herr-skor",
                finalUrl = null,
                title = null,
                error = "Navigation timeout after 30000 ms",
                startedAt = now.minusSeconds(14 * 60),
                finishedAt = now.minusSeconds(14 * 60).plusMillis(30_000),
                loadedMs = null,
                htmlSizeBytes = null,
            ),
            ok("news.ycombinator.com", "/newest", "New Links | Hacker News", 640, 22, 88_412),
            ok("wikipedia.org", "/wiki/Meerkat", "Meerkat — Wikipedia", 950, 31, 402_118),
            ok("openstreetmap.org", "/", "OpenStreetMap", 1_480, 47, 310_552),
            ok("gutenberg.org", "/ebooks/84", "Frankenstein — Project Gutenberg", 720, 58, 156_902),
        )
    }
}
