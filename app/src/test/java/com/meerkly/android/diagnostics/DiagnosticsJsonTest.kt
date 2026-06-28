package com.meerkly.android.diagnostics

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
                geckoViewVersion = "152.0",
                profileStatus = "app-scoped default GeckoRuntime profile",
                latestNavigation = null,
                logRetention = linkedMapOf("keep_days" to 7L),
                generatedAt = "2026-06-28T00:00:00Z",
            )
        )

        assertTrue(json.contains("\"machine_id\":\"m1\""))
        assertTrue(json.contains("\"app_version\":\"1.0\""))
        assertTrue(json.contains("\"android_sdk_int\":34"))
        assertTrue(json.contains("\"geckoview_version\":\"152.0\""))
        assertTrue(json.contains("\"latest_navigation\":null"))
        assertTrue(json.contains("\"log_retention\":{\"keep_days\":7}"))
    }
}
