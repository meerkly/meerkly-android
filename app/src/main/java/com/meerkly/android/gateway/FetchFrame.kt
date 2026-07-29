package com.meerkly.android.gateway

import org.json.JSONArray
import org.json.JSONObject

/**
 * A gateway `fetch` frame parsed into the exact job the worker will run.
 * [rulesJson] stays a JSON string because that's how the rules ride to the
 * content script (`spec` message — objects don't survive the content-process
 * boundary, see GeckoBrowserManager).
 */
data class FetchJob(
    val jobId: String,
    val url: String,
    val waitFor: String,
    val settleMs: Int,
    val rulesJson: String,
    val detectMs: Int,
)

/**
 * Canonical fetch-frame parsing. The source of truth is the shared spec in the
 * api-gateway repo (spec/fetch-job.schema.json + spec/vectors/); the
 * conformance test (FetchFrameConformanceTest) binds this file to it. The
 * desktop port implements the identical semantics (src/shared/fetchSpec.ts).
 * The effective settle/detect math (default 5000/200, cap 25000) lives in the
 * extension's content.js — the spec constants below document it and the
 * conformance test checks content.js hasn't drifted.
 */
object FetchFrame {
    const val WAIT_FOR_DEFAULT = "stable"
    const val SETTLE_DEFAULT_MS = 5000
    const val SETTLE_MAX_MS = 25000
    const val DETECT_DEFAULT_MS = 200
    const val DETECT_MAX_MS = 25000

    /**
     * Parse a gateway `fetch` frame, applying the spec's defaults (empty
     * waitFor -> stable, missing numbers -> 0 = "worker default", missing
     * rules -> []). Returns null when the frame is structurally unusable
     * (no jobId/url) — URL content validation stays with the caller.
     */
    fun parse(msg: JSONObject): FetchJob? {
        val jobId = msg.optString("jobId")
        val url = msg.optString("url")
        if (jobId.isEmpty() || url.isEmpty()) return null
        val waitFor = msg.optString("waitFor").ifEmpty { WAIT_FOR_DEFAULT }
        val settleMs = msg.optInt("settleMs").coerceAtLeast(0)
        val detectMs = msg.optInt("detectMs").coerceAtLeast(0)
        val rules = JSONArray()
        val raw = msg.optJSONArray("waitRules")
        if (raw != null) {
            for (i in 0 until raw.length()) {
                val r = raw.optJSONObject(i) ?: continue
                val guard = r.optString("if")
                if (guard.isEmpty()) continue
                rules.put(JSONObject().put("if", guard).put("then", r.optString("then")))
            }
        }
        return FetchJob(jobId, url, waitFor, settleMs, rules.toString(), detectMs)
    }
}
