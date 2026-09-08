package com.meerkly.android.model

/**
 * What the account has earned, from /api/v1/earnings.
 *
 * Replaces the 1.x `Credits`, whose economy (500,000 credits = $1, one credit
 * per fetched page) does not exist on this network. Money here is bytes priced
 * per GB, and the server is the only authority on it.
 */
data class Earnings(
    val unpaidUsd: Double,
    val lifetimeUsd: Double,
    /**
     * Money shared but not yet priced into the ledger. [lifetimeUsd] is settled
     * only, so the two must be shown together or a user who has just started
     * earning sees a zero next to a device that is visibly working.
     */
    val pendingUsd: Double,
    val bytesShared: Long,
    val settledBytes: Long,
    val usdPerGb: Double,
    val minimumPayoutUsd: Double,
    val canRequestPayout: Boolean,
    /** The window the per-device figures cover — 30 at the time of writing. */
    val devicesWindowDays: Int,
    val devices: List<DeviceEarnings>,
) {
    /** This install's row, or null when the gateway has not yet reported it. */
    fun forDevice(deviceId: String): DeviceEarnings? =
        devices.firstOrNull { it.deviceId == deviceId }
}

/**
 * One device's share. A *display* attribution: device ids are self-reported and
 * spoofable, so the server never pays against them, and neither may this app
 * imply that it does.
 */
data class DeviceEarnings(
    val deviceId: String,
    val label: String,
    val online: Boolean,
    /**
     * Windowed, not lifetime — [Earnings.devicesWindowDays] says how wide — and
     * unlike the account totals these include unsettled money. The UI must say
     * so; a figure here will not match [Earnings.lifetimeUsd] and is not meant to.
     */
    val bytes30d: Long,
    val usd30d: Double,
    val pending: Boolean,
)

/**
 * Whether we actually know what the account has earned.
 *
 * "Not loaded" and "zero" are the same value in a Double, and rendering the
 * first as the second tells someone their money is gone when the server is
 * merely unreachable. The app may report the last figure the server gave it, or
 * admit it does not know — never assert a zero.
 */
sealed interface EarningsState {
    data object Unknown : EarningsState

    data class Loaded(val earnings: Earnings) : EarningsState

    val earningsOrNull: Earnings?
        get() = (this as? Loaded)?.earnings
}
