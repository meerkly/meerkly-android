package com.meerkly.android.data

import com.meerkly.android.logging.AppLogger
import com.meerkly.android.util.MiniJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.Instant

/**
 * Device registration — binds this install's machineId to the signed-in user's
 * Meerkly account (the Android counterpart of the desktop's
 * deviceRegistration.ts, with identical semantics). POST /api/devices (OAuth
 * bearer, worker scope) returns a device token proving ownership of the
 * machineId; the gateway requires it on every worker connection. The token is
 * persisted via [SecureStore]. Sign-out does NOT unregister the device —
 * ownership outlives sessions; login exists only to verify it.
 */
class DeviceRegistrationManager(
    accountBaseUrl: String,
    private val machineId: String,
    private val deviceInfo: () -> DeviceInfo,
    private val logger: AppLogger,
    private val store: SecureStore,
    private val client: OkHttpClient = OkHttpClient(),
) {
    private val baseUrl = accountBaseUrl.trimEnd('/')

    /** Result of a registration attempt, merged into the UI's auth status. */
    data class LinkStatus(val deviceLinked: Boolean, val deviceLinkError: String? = null)

    @Volatile
    private var lastError: String? = null

    /**
     * The stored device token, or null when this install isn't registered yet.
     * The gateway client calls this on every (re)connect, so a rotated token is
     * picked up without a restart.
     */
    fun getDeviceToken(): String? {
        val raw = store.get(KEY_DEVICE) ?: return null
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        // A restored backup or changed machine id means this token proves the
        // wrong machineId; ignore it and re-register.
        if (json.optString("machineId") != machineId) {
            logger.warn("device.token_machine_mismatch")
            return null
        }
        return json.optString("deviceToken").takeIf { it.isNotBlank() }
    }

    /** Current link state for the UI. */
    fun linkStatus(): LinkStatus =
        if (getDeviceToken() != null) LinkStatus(deviceLinked = true)
        else LinkStatus(deviceLinked = false, deviceLinkError = lastError)

    /**
     * Claims this device for the signed-in user. Safe to call repeatedly: the
     * server is idempotent for the same owner (rotating the token) and rejects
     * cross-account claims with 409. Never throws — failures land in the
     * returned/link status.
     */
    suspend fun register(accessToken: String): LinkStatus = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) {
            lastError = ERROR_RETRYABLE
            logger.warn("device.register_skipped", mapOf("reason" to "no ACCOUNT_BASE_URL"))
            return@withContext linkStatus()
        }

        val info = deviceInfo()
        val body = MiniJson.encode(
            mapOf(
                "machine_id" to machineId,
                "platform" to "android",
                "name" to info.name,
                "device_model" to info.deviceModel,
                "os" to info.os,
                "arch" to info.arch,
                "app_version" to info.appVersion,
                "engine_version" to info.engineVersion,
            ),
        )
        val request = Request.Builder()
            .url("$baseUrl/api/devices")
            .header("Authorization", "Bearer $accessToken")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val response = runCatching { client.newCall(request).execute() }.getOrElse { e ->
            logger.warn("device.register_unreachable", mapOf("error" to e.message))
            lastError = ERROR_UNREACHABLE
            return@withContext linkStatus()
        }

        response.use { res ->
            val text = runCatching { res.body?.string() }.getOrNull() ?: ""
            when {
                res.code == 200 || res.code == 201 -> {
                    val token = runCatching { JSONObject(text).optString("device_token") }
                        .getOrNull()?.takeIf { it.isNotBlank() }
                    if (token == null) {
                        logger.error("device.register_bad_response", mapOf("status" to res.code))
                        lastError = ERROR_UNEXPECTED
                    } else {
                        store.put(
                            KEY_DEVICE,
                            MiniJson.encode(
                                mapOf(
                                    "deviceToken" to token,
                                    "machineId" to machineId,
                                    "registeredAt" to Instant.now().toString(),
                                ),
                            ),
                        )
                        lastError = null
                        logger.info("device.registered", mapOf("machineId" to machineId))
                    }
                }
                res.code == 409 -> {
                    logger.warn("device.already_linked", mapOf("machineId" to machineId))
                    lastError = ERROR_ALREADY_LINKED
                }
                res.code == 403 -> {
                    // A token from before the worker scope existed can't register.
                    logger.warn("device.register_forbidden")
                    lastError = ERROR_STALE_SCOPE
                }
                else -> {
                    logger.warn("device.register_rejected", mapOf("status" to res.code, "body" to text.take(200)))
                    lastError = ERROR_RETRYABLE
                }
            }
        }
        linkStatus()
    }

    companion object {
        private const val KEY_DEVICE = "device_registration"

        // Desktop-mirrored user-facing error strings (deviceRegistration.ts).
        const val ERROR_ALREADY_LINKED = "This device is already linked to another account."
        const val ERROR_STALE_SCOPE = "Please sign out and back in to link this device."
        const val ERROR_UNREACHABLE = "Could not reach Meerkly to link this device. It will be retried."
        const val ERROR_RETRYABLE = "Linking this device failed. It will be retried."
        const val ERROR_UNEXPECTED = "Linking this device failed unexpectedly. Please try again."
    }
}
