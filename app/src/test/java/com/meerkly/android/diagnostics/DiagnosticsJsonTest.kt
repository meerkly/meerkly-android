package com.meerkly.android.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsJsonTest {

    @Test
    fun includesCoreMetadataFields() {
        val json = DiagnosticsJson.build(
            DiagnosticsMeta(
                machineId = "m1",
                appVersion = "1.0",
                androidSdkInt = 34,
                deviceModel = "Pixel 8",
                locale = "en-US",
                timezone = "UTC",
                proxyState = "Connected",
                clientKey = "ck_123",
                logRetention = linkedMapOf("keep_days" to 7L),
                generatedAt = "2026-06-28T00:00:00Z",
            )
        )

        assertTrue(json.contains("\"machine_id\":\"m1\""))
        assertTrue(json.contains("\"app_version\":\"1.0\""))
        assertTrue(json.contains("\"android_sdk_int\":34"))
        assertTrue(json.contains("\"proxy_state\":\"Connected\""))
        assertTrue(json.contains("\"client_key\":\"ck_123\""))
        assertTrue(json.contains("\"log_retention\":{\"keep_days\":7}"))
    }

    @Test
    fun `no publisher id ever appears in the payload`() {
        // Redaction rule: a diagnostics ZIP is something a user emails to
        // support, so it must never carry the account identifier.
        val json = DiagnosticsJson.build(
            DiagnosticsMeta(
                machineId = "m1",
                appVersion = "1.0",
                androidSdkInt = 34,
                deviceModel = "Pixel 8",
                locale = "en-US",
                timezone = "UTC",
                proxyState = "Disconnected",
                clientKey = null,
                logRetention = linkedMapOf("keep_days" to 7L),
                generatedAt = "2026-06-28T00:00:00Z",
            )
        )

        assertFalse(json.contains("publisher"))
    }
}
