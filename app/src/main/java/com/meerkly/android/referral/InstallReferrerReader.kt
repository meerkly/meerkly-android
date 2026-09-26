package com.meerkly.android.referral

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerClient.InstallReferrerResponse
import com.android.installreferrer.api.InstallReferrerStateListener
import com.meerkly.android.data.SecureStore
import com.meerkly.android.logging.AppLogger

/**
 * Reads the Play install referrer once per install and keeps the invite code
 * it carries (if any) in [SecureStore] under [KEY_PENDING_CODE], where
 * [com.meerkly.android.auth.AuthManager.signInIntent] picks it up.
 *
 * Every failure is silent: a sideloaded APK, a device without Play, or a
 * busy referrer service must never surface anything to the user. The "done"
 * flag is only set once the service has given a definite answer, so a
 * transient SERVICE_UNAVAILABLE is retried on the next launch.
 */
class InstallReferrerReader(
    private val context: Context,
    private val store: SecureStore,
    private val logger: AppLogger,
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
) {
    fun readOnce() {
        if (prefs.getBoolean(KEY_CHECKED, false)) return
        runCatching {
            val client = InstallReferrerClient.newBuilder(context).build()
            client.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    runCatching {
                        when (responseCode) {
                            InstallReferrerResponse.OK -> {
                                val code = ReferralCode.fromInstallReferrer(
                                    client.installReferrer.installReferrer,
                                )
                                if (code != null) store.put(KEY_PENDING_CODE, code)
                                logger.info("referral.install_referrer", mapOf("has_code" to (code != null)))
                                markChecked()
                            }
                            // Permanent answers: no referrer to be had on this device.
                            InstallReferrerResponse.FEATURE_NOT_SUPPORTED,
                            InstallReferrerResponse.DEVELOPER_ERROR,
                            -> markChecked()
                            else -> Unit // transient; try again next launch
                        }
                    }.onFailure {
                        logger.warn("referral.install_referrer_failed", mapOf("error" to it.message))
                    }
                    runCatching { client.endConnection() }
                }

                override fun onInstallReferrerServiceDisconnected() = Unit
            })
        }.onFailure {
            logger.warn("referral.install_referrer_failed", mapOf("error" to it.message))
        }
    }

    private fun markChecked() {
        prefs.edit { putBoolean(KEY_CHECKED, true) }
    }

    companion object {
        /** SecureStore key for an invite code not yet sent with a sign-in. */
        const val KEY_PENDING_CODE = "pending_referral_code"
        private const val PREFS = "meerkly_prefs"
        private const val KEY_CHECKED = "install_referrer_checked"
    }
}
