package com.meerkly.android.gateway

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Binds this port to the canonical protocol spec in the api-gateway repo
 * (spec/fetch-job.schema.json + spec/vectors/fetch-frames.json). The gateway
 * and desktop repos run their own tests against the same files — that is what
 * keeps all three implementations in lockstep. Locally the sibling checkout is
 * used; set SPEC_DIR when the repos aren't siblings.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FetchFrameConformanceTest {

    private val specDir: File by lazy {
        System.getenv("SPEC_DIR")?.let { return@lazy File(it) }
        // Gradle test working dir is app/; walk up looking for the sibling repo.
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null) {
            val candidate = File(dir, "api-gateway/spec")
            if (candidate.isDirectory) return@lazy candidate
            dir = dir.parentFile
        }
        error("Canonical spec not found — clone api-gateway as a sibling repo or set SPEC_DIR.")
    }

    @Test
    fun `frame vectors parse to the spec's expected job`() {
        val vectors = JSONObject(File(specDir, "vectors/fetch-frames.json").readText())
        val cases = vectors.getJSONArray("cases")
        assertTrue(cases.length() > 0)
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val name = case.getString("name")
            val job = FetchFrame.parse(case.getJSONObject("frame"))
                ?: throw AssertionError("$name: parse returned null")
            val expected = case.getJSONObject("parsed")
            assertEquals(name, expected.getString("jobId"), job.jobId)
            assertEquals(name, expected.getString("url"), job.url)
            assertEquals(name, expected.getString("waitFor"), job.waitFor)
            assertEquals(name, expected.getInt("settleMs"), job.settleMs)
            assertEquals(name, expected.getInt("detectMs"), job.detectMs)
            val expectedRules = expected.getJSONArray("waitRules")
            val actualRules = org.json.JSONArray(job.rulesJson)
            assertEquals(name, expectedRules.length(), actualRules.length())
            for (j in 0 until expectedRules.length()) {
                val e = expectedRules.getJSONObject(j)
                val a = actualRules.getJSONObject(j)
                assertEquals(name, e.getString("if"), a.getString("if"))
                assertEquals(name, e.optString("then"), a.optString("then"))
            }
        }
    }

    @Test
    fun `structurally unusable frames are rejected`() {
        assertNull(FetchFrame.parse(JSONObject("""{"type":"fetch","url":"https://example.com"}""")))
        assertNull(FetchFrame.parse(JSONObject("""{"type":"fetch","jobId":"j"}""")))
    }

    @Test
    fun `constants match the canonical schema`() {
        val schema = JSONObject(File(specDir, "fetch-job.schema.json").readText())
        val c = schema.getJSONObject("\$defs").getJSONObject("constants")
        assertEquals(c.getInt("settleDefaultMs"), FetchFrame.SETTLE_DEFAULT_MS)
        assertEquals(c.getInt("settleMaxMs"), FetchFrame.SETTLE_MAX_MS)
        assertEquals(c.getInt("detectDefaultMs"), FetchFrame.DETECT_DEFAULT_MS)
        assertEquals(c.getInt("detectMaxMs"), FetchFrame.DETECT_MAX_MS)
        assertEquals(c.getString("waitForDefault"), FetchFrame.WAIT_FOR_DEFAULT)
    }

    @Test
    fun `content script implements the spec's effective math`() {
        // The settle/detect defaults+caps run inside the extension's content
        // script (JS), which no JVM test can execute — pin its literals to the
        // spec constants instead so silent drift still fails a build.
        var dir: File? = File(System.getProperty("user.dir"))
        var contentJs: File? = null
        while (dir != null) {
            val candidate = File(dir, "app/src/main/assets/extensions/extractor/content.js")
            if (candidate.isFile) { contentJs = candidate; break }
            dir = dir.parentFile
        }
        val src = (contentJs ?: error("content.js not found")).readText()
        assertTrue("detect default drifted", src.contains("detectMs || ${FetchFrame.DETECT_DEFAULT_MS}"))
        assertTrue("settle default drifted", src.contains("maxMs || ${FetchFrame.SETTLE_DEFAULT_MS}"))
        assertTrue("cap drifted", src.contains("${FetchFrame.SETTLE_MAX_MS}"))
    }
}
