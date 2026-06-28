package com.meerkly.android.data

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * Stable, app-scoped per-install identifier. Generated once and persisted in SharedPreferences.
 * Never derived from hardware identifiers.
 */
object MachineIdManager {
    private const val PREFS = "meerkly_prefs"
    private const val KEY_MACHINE_ID = "machine_id"

    fun getMachineId(context: Context): String =
        getMachineId(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))

    /** Overload taking SharedPreferences directly so the logic is testable without a full Context. */
    fun getMachineId(prefs: SharedPreferences): String {
        prefs.getString(KEY_MACHINE_ID, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val id = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_MACHINE_ID, id).apply()
        return id
    }
}
