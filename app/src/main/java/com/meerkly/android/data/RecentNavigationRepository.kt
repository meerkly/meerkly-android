package com.meerkly.android.data

import com.meerkly.android.model.NavigationResult
import com.meerkly.android.util.MiniJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * In-memory ring of the most recent navigation results (newest first), capped at [maxEntries].
 * Exposes a StateFlow for the UI and a write-only JSON snapshot for diagnostics export. The live
 * ring starts empty on each launch — on restart we do not assume an unfinished navigation
 * succeeded; the persisted snapshot is for diagnostics only.
 */
class RecentNavigationRepository(private val maxEntries: Int = 20) {

    private val _recent = MutableStateFlow<List<NavigationResult>>(emptyList())
    val recent: StateFlow<List<NavigationResult>> = _recent.asStateFlow()

    @Synchronized
    fun record(result: NavigationResult) {
        _recent.value = (listOf(result) + _recent.value).take(maxEntries)
    }

    fun latest(): NavigationResult? = _recent.value.firstOrNull()

    fun snapshotJsonMaps(): List<Map<String, Any?>> = _recent.value.map { it.toJsonMap() }

    /** Best-effort write of the current snapshot for inclusion in diagnostics. */
    fun persist(file: File) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(MiniJson.encode(snapshotJsonMaps()))
        }
    }
}
