package com.meerkly.android.data

import com.meerkly.android.model.NavigationResult
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class RecentNavigationRepositoryTest {

    private fun result(url: String) = NavigationResult(
        success = true,
        requestedUrl = url,
        finalUrl = url,
        title = "t",
        error = null,
        startedAt = Instant.EPOCH,
        finishedAt = Instant.EPOCH,
        loadedMs = 1,
        htmlSizeBytes = null,
    )

    @Test
    fun keepsNewestFirstAndCapsAtMax() {
        val repo = RecentNavigationRepository(maxEntries = 3)
        repo.record(result("https://a"))
        repo.record(result("https://b"))
        repo.record(result("https://c"))
        repo.record(result("https://d"))

        val urls = repo.recent.value.map { it.requestedUrl }
        assertEquals(listOf("https://d", "https://c", "https://b"), urls)
        assertEquals("https://d", repo.latest()?.requestedUrl)
    }

    @Test
    fun snapshotMapsMirrorRecent() {
        val repo = RecentNavigationRepository()
        repo.record(result("https://x"))
        val maps = repo.snapshotJsonMaps()
        assertEquals(1, maps.size)
        assertEquals("https://x", maps.first()["requested_url"])
    }
}
