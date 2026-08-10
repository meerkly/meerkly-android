package com.meerkly.android.ui

import androidx.annotation.StringRes
import com.meerkly.android.R

/** What tapping an unfinished step should do. */
enum class SetupAction {
    /** Ask the system for POST_NOTIFICATIONS (33+). */
    RequestNotifications,

    /**
     * The permission was refused for good, so the system dialog would silently
     * no-op — send the user to app settings instead. Without this the step
     * could never be ticked and the card would never hide.
     */
    OpenNotificationSettings,

    /** System dialog to exempt Meerkly from battery optimisation (Doze). */
    RequestBatteryExemption,
}

data class SetupStep(
    @StringRes val title: Int,
    @StringRes val note: Int,
    val done: Boolean,
    /** null for steps the user can't act on here (already-done ticks). */
    val action: SetupAction?,
)

/**
 * The onboarding checklist shown on the dashboard until setup is complete.
 *
 * Kept as pure data — no Context, no Compose — so every combination is
 * unit-testable. Includes the already-satisfied steps on purpose: seeing
 * "Signed in ✓" is what makes the remaining work feel finishable.
 */
object SetupChecklist {

    /** API level at which POST_NOTIFICATIONS became a runtime permission. */
    const val NOTIFICATION_PERMISSION_SDK = 33

    fun steps(
        deviceLinked: Boolean,
        notificationsGranted: Boolean,
        notificationsPermanentlyDenied: Boolean,
        batteryExempt: Boolean,
        sdkInt: Int,
    ): List<SetupStep> {
        // Below 33 notifications are granted at install time — nothing to ask,
        // so the step is simply done rather than hidden (keeps the count stable).
        val notificationsDone = notificationsGranted || sdkInt < NOTIFICATION_PERMISSION_SDK
        return listOf(
            SetupStep(
                title = R.string.setup_signed_in_title,
                note = R.string.setup_signed_in_note,
                done = true,
                action = null,
            ),
            SetupStep(
                title = R.string.setup_linked_title,
                note = R.string.setup_linked_note,
                done = deviceLinked,
                // Linking heals itself via the coordinator; the user can't
                // usefully retry it from here.
                action = null,
            ),
            SetupStep(
                title = R.string.setup_notifications_title,
                note = R.string.setup_notifications_note,
                done = notificationsDone,
                action = when {
                    notificationsDone -> null
                    notificationsPermanentlyDenied -> SetupAction.OpenNotificationSettings
                    else -> SetupAction.RequestNotifications
                },
            ),
            SetupStep(
                title = R.string.setup_battery_title,
                note = R.string.setup_battery_note,
                done = batteryExempt,
                action = if (batteryExempt) null else SetupAction.RequestBatteryExemption,
            ),
        )
    }

    fun allDone(steps: List<SetupStep>): Boolean = steps.all { it.done }

    fun doneCount(steps: List<SetupStep>): Int = steps.count { it.done }
}
