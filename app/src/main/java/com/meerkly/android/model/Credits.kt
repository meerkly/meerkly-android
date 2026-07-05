package com.meerkly.android.model

/**
 * Earnings for the signed-in user, from the account service's /api/credits.
 * `dollars` is the display value of `totalCredits` (500,000 credits = $1).
 */
data class Credits(
    val totalCredits: Long,
    val dollars: Double,
    val devices: List<DeviceCredits>,
) {
    /** Credits earned by this install (its machine id), or 0 if it has none yet. */
    fun creditsFor(machineId: String): Long =
        devices.firstOrNull { it.machineId == machineId }?.credits ?: 0L

    companion object {
        const val CREDITS_PER_DOLLAR = 500_000.0
    }
}

data class DeviceCredits(val machineId: String, val credits: Long)
