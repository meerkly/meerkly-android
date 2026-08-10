package com.meerkly.android.worker

import android.content.Context
import android.content.SharedPreferences

/**
 * User intent for the background worker, persisted in `meerkly_prefs` (plain
 * keys, same file/pattern as [com.meerkly.android.data.MachineIdManager] — the
 * file is excluded from Auto Backup, which is fine: worker preferences are
 * per-install, like the machineId next to them).
 *
 * [workerEnabled] is the single switch every start path checks — Activity
 * foreground, post-pairing, boot receiver, STICKY restart. It defaults to true
 * ("always run while paired"); an explicit user Stop flips it false and it
 * stays false until the user turns the worker back on in the app. Nothing else
 * may write it.
 *
 * There is deliberately no "nudge dismissed" flag: the setup checklist hides
 * itself by being completed, not by being dismissed.
 */
class WorkerPrefs(private val prefs: SharedPreferences) {

    constructor(context: Context) :
        this(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))

    /** Does the user want the worker running? Sticky across restarts/reboots. */
    var workerEnabled: Boolean
        get() = prefs.getBoolean(KEY_WORKER_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_WORKER_ENABLED, value).apply()
        }

    private companion object {
        const val PREFS = "meerkly_prefs"
        const val KEY_WORKER_ENABLED = "worker_enabled"
    }
}
