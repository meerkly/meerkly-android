package com.meerkly.android.auth

import com.meerkly.android.model.Earnings
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The JSON contracts the app depends on. Parsing is tested against a real
 * socket rather than a hand-built string so a change in either shape fails
 * here rather than on a user's phone.
 *
 * Robolectric, not a plain JUnit runner: this never touches a Context, but
 * org.json ships as a stub on the classpath a plain unit test compiles and
 * runs against — every JSONObject method throws "not mocked". Robolectric
 * substitutes the real platform jar, which has a real org.json.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthManagerApiTest {

    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun body(json: String) = MockResponse().setResponseCode(200).setBody(json)

    @Test
    fun `me returns the publisher id`() {
        server.enqueue(body("""{"id":1,"email":"a@b.com","publisher_id":"pub_abc123"}"""))

        val json = JSONObject(fetch("/api/v1/me"))

        assertEquals("pub_abc123", AuthManager.parsePublisherId(json))
        assertEquals("a@b.com", AuthManager.parseEmail(json))
    }

    @Test
    fun `a me response with no publisher id yields null, not an empty string`() {
        val json = JSONObject("""{"id":1,"email":"a@b.com"}""")

        assertNull(AuthManager.parsePublisherId(json))
    }

    @Test
    fun `earnings parses the full contract`() {
        val json = JSONObject(
            """
            {"unpaid_usd":1.23,"lifetime_usd":4.56,"pending_usd":0.11,
             "bytes_shared":123456789,"settled_bytes":120000000,
             "usd_per_gb":0.35,"minimum_payout_usd":5.0,
             "can_request_payout":false,"devices_window_days":30,
             "devices":[{"device_id":"dev_a","label":"Pixel 8","online":true,
                         "bytes_30d":1000,"usd_30d":0.42,"pending":true}]}
            """.trimIndent(),
        )

        val earnings = AuthManager.parseEarnings(json)

        assertEquals(1.23, earnings.unpaidUsd, 0.0001)
        assertEquals(4.56, earnings.lifetimeUsd, 0.0001)
        assertEquals(0.11, earnings.pendingUsd, 0.0001)
        assertEquals(30, earnings.devicesWindowDays)
        assertEquals(123456789L, earnings.bytesShared)
        assertEquals(0.35, earnings.usdPerGb, 0.0001)
        assertEquals(5.0, earnings.minimumPayoutUsd, 0.0001)
        assertEquals(false, earnings.canRequestPayout)

        val device = earnings.forDevice("dev_a")!!
        assertEquals("Pixel 8", device.label)
        assertTrue(device.online)
        assertEquals(1000L, device.bytes30d)
        assertEquals(0.42, device.usd30d, 0.0001)
        assertTrue(device.pending)
    }

    @Test
    fun `earnings with no devices parses to an empty list, not a crash`() {
        val json = JSONObject("""{"unpaid_usd":0.0,"lifetime_usd":0.0,"bytes_shared":0}""")

        val earnings = AuthManager.parseEarnings(json)

        assertEquals(emptyList<Any>(), earnings.devices)
        assertNull(earnings.forDevice("dev_missing"))
    }

    @Test
    fun `me parses the referral code and url when present`() {
        server.enqueue(
            body(
                """{"email":"a@b.com","publisher_id":"pub_abc","referral_code":"ABCDEFGH",
                   "referral_url":"https://meerkly.com/r/ABCDEFGH"}""",
            ),
        )

        val account = AuthManager.parseAccount(JSONObject(fetch("/api/v1/me")))!!

        assertEquals("pub_abc", account.publisherId)
        assertEquals("ABCDEFGH", account.referralCode)
        assertEquals("https://meerkly.com/r/ABCDEFGH", account.referralUrl)
    }

    @Test
    fun `me without referral fields, or with nulls, yields null referral values`() {
        val absent = AuthManager.parseAccount(JSONObject("""{"email":"a@b.com","publisher_id":"pub_abc"}"""))!!
        assertNull(absent.referralCode)
        assertNull(absent.referralUrl)

        val nulls = AuthManager.parseAccount(
            JSONObject("""{"email":"a@b.com","referral_code":null,"referral_url":null}"""),
        )!!
        assertNull(nulls.referralCode)
        assertNull(nulls.referralUrl)
    }

    @Test
    fun `a malformed referral code or a non-https url is ignored`() {
        val json = JSONObject("""{"email":"a@b.com","referral_code":"abc","referral_url":"http://x/r/abc"}""")
        assertNull(AuthManager.parseReferralCode(json))
        assertNull(AuthManager.parseReferralUrl(json))
    }

    @Test
    fun `me with no email is not an account`() {
        assertNull(AuthManager.parseAccount(JSONObject("""{"publisher_id":"pub_abc"}""")))
    }

    @Test
    fun `earnings parses the referral fields`() {
        server.enqueue(
            body(
                """{"unpaid_usd":2.5,"lifetime_usd":4.0,"referrals_enabled":true,
                   "referral_usd":0.5,"referral_held_usd":0.2,"referral_lifetime_usd":1.25,
                   "referrals":{"level1":3,"level2":7},
                   "referral_rates":{"level1":0.1,"level2":0.05}}""",
            ),
        )

        val earnings = AuthManager.parseEarnings(JSONObject(fetch("/api/v1/earnings")))

        assertTrue(earnings.referralsEnabled)
        assertEquals(0.5, earnings.referralUsd, 0.0001)
        assertEquals(0.2, earnings.referralHeldUsd, 0.0001)
        assertEquals(1.25, earnings.referralLifetimeUsd, 0.0001)
        assertEquals(3, earnings.referralCounts.level1)
        assertEquals(7, earnings.referralCounts.level2)
        assertEquals(0.1, earnings.referralRates.level1!!, 0.0001)
        assertEquals(0.05, earnings.referralRates.level2!!, 0.0001)
        // lifetime_usd keeps meaning own traffic only.
        assertEquals(4.0, earnings.lifetimeUsd, 0.0001)
    }

    @Test
    fun `earnings from an older server defaults the referral fields to none`() {
        val earnings = AuthManager.parseEarnings(JSONObject("""{"unpaid_usd":1.0,"lifetime_usd":1.0}"""))

        assertEquals(false, earnings.referralsEnabled)
        assertEquals(0.0, earnings.referralUsd, 0.0)
        assertEquals(0.0, earnings.referralHeldUsd, 0.0)
        assertEquals(0.0, earnings.referralLifetimeUsd, 0.0)
        assertEquals(0, earnings.referralCounts.level1)
        assertEquals(0, earnings.referralCounts.level2)
        assertNull(earnings.referralRates.level1)
        assertNull(earnings.referralRates.level2)
    }

    // isEmailUnverified is the one branch of the 403 handling with a
    // user-visible consequence — it decides whether a failed sign-in gets
    // told to confirm their email, or just told "sign-in failed" again. It
    // is pure specifically so this distinction can be pinned down without
    // standing up a real AuthManager (Context + SecureStore + AppAuth).

    @Test
    fun `a 403 with the email-unverified error is treated as email-unverified`() {
        assertTrue(
            AuthManager.isEmailUnverified(403, """{"error":"email_verification_required"}"""),
        )
    }

    @Test
    fun `a 403 with a different error is not treated as email-unverified`() {
        assertEquals(false, AuthManager.isEmailUnverified(403, """{"error":"invalid_token"}"""))
    }

    @Test
    fun `a 403 with a non-JSON body is not treated as email-unverified, and does not throw`() {
        assertEquals(false, AuthManager.isEmailUnverified(403, "not json"))
    }

    @Test
    fun `a 200 is never treated as email-unverified, even with the matching body`() {
        assertEquals(
            false,
            AuthManager.isEmailUnverified(200, """{"error":"email_verification_required"}"""),
        )
    }

    @Test
    fun `a null body is not treated as email-unverified`() {
        assertEquals(false, AuthManager.isEmailUnverified(403, null))
    }

    @Test
    fun `the email-unverified message is the actionable one, not the generic failure text`() {
        // completeSignIn itself needs a live AuthManager (Context, SecureStore,
        // AppAuth's AuthorizationService) to exercise, which the brief asks us
        // not to build just for this. This asserts against the real constant
        // its 403 branch returns, so a regression that reused the generic
        // failure string still fails a test.
        assertTrue(AuthManager.MESSAGE_EMAIL_UNVERIFIED.contains("Confirm your email"))
        assertTrue(AuthManager.MESSAGE_EMAIL_UNVERIFIED != "Sign-in failed. Please try again.")
    }

    // sessionHostMatches decides whether a restored AuthState is still usable
    // against this build's configured account host — the load()-time guard
    // that discards a 1.x session pointed at a retired host instead of
    // leaving the user permanently signed-in-but-broken.

    @Test
    fun `a session token endpoint on the configured host matches`() {
        assertTrue(
            AuthManager.sessionHostMatches(
                "https://dashboard.meerkly.com",
                "https://dashboard.meerkly.com/oauth/token",
            ),
        )
    }

    @Test
    fun `a session token endpoint on a different host does not match`() {
        assertEquals(
            false,
            AuthManager.sessionHostMatches(
                "https://dashboard.meerkly.com",
                "https://account.meerkly.com/oauth/token",
            ),
        )
    }

    @Test
    fun `a null token endpoint is treated as usable, not evidence of a host change`() {
        assertTrue(AuthManager.sessionHostMatches("https://dashboard.meerkly.com", null))
    }

    @Test
    fun `a session token endpoint on the same host but a different port does not match`() {
        assertEquals(
            false,
            AuthManager.sessionHostMatches(
                "https://dashboard.meerkly.com:3000",
                "https://dashboard.meerkly.com:8443/oauth/token",
            ),
        )
    }

    private fun fetch(path: String): String =
        okhttp3.OkHttpClient().newCall(
            okhttp3.Request.Builder().url(server.url(path)).build(),
        ).execute().use { it.body!!.string() }
}
