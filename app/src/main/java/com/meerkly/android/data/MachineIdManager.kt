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
    /**
     * The convention every host on this network uses for a device id
     * (meerkly-agent writes the same shape into its state dir). The gateway
     * carries it through to the dashboard's devices table.
     */
    private const val PREFIX = "dev_"

    fun getMachineId(context: Context): String =
        getMachineId(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))

    /** Overload taking SharedPreferences directly so the logic is testable without a full Context. */
    fun getMachineId(prefs: SharedPreferences): String {
        val stored = prefs.getString(KEY_MACHINE_ID, null)?.takeIf { it.isNotBlank() }
        if (stored != null) {
            if (stored.startsWith(PREFIX)) return stored
            // A 1.x install holds a bare UUID. Prefix it in place rather than
            // minting a fresh id: a new id would orphan the device row the
            // account may already have named, and the user would find their
            // phone listed twice.
            val migrated = PREFIX + stored
            prefs.edit().putString(KEY_MACHINE_ID, migrated).apply()
            return migrated
        }
        val id = PREFIX + UUID.randomUUID().toString()
        prefs.edit().putString(KEY_MACHINE_ID, id).apply()
        return id
    }
}
