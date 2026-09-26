package com.meerkly.android.referral

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferralCodeTest {

    @Test
    fun `ref is read from a plain referrer query`() {
        assertEquals("ABCDEFGH", ReferralCode.fromInstallReferrer("ref=ABCDEFGH&utm_source=meerkly"))
    }

    @Test
    fun `ref need not be the first parameter`() {
        assertEquals("ABCD2345", ReferralCode.fromInstallReferrer("utm_source=x&ref=ABCD2345"))
    }

    @Test
    fun `a referrer that arrives URL-encoded once more still yields the code`() {
        assertEquals("ABCDEFGH", ReferralCode.fromInstallReferrer("ref%3DABCDEFGH%26utm_source%3Dmeerkly"))
    }

    @Test
    fun `lower-case input is normalised`() {
        assertEquals("ABCDEFGH", ReferralCode.fromInstallReferrer("ref=abcdefgh"))
    }

    @Test
    fun `the Play default organic referrer has no code`() {
        assertNull(ReferralCode.fromInstallReferrer("utm_source=google-play&utm_medium=organic"))
    }

    @Test
    fun `null, blank and garbage yield null without throwing`() {
        assertNull(ReferralCode.fromInstallReferrer(null))
        assertNull(ReferralCode.fromInstallReferrer(""))
        assertNull(ReferralCode.fromInstallReferrer("%%%"))
        assertNull(ReferralCode.fromInstallReferrer("ref="))
        assertNull(ReferralCode.fromInstallReferrer("=ABCDEFGH"))
    }

    @Test
    fun `codes of the wrong shape are rejected`() {
        assertNull(ReferralCode.fromInstallReferrer("ref=ABCDEFG")) // 7 chars
        assertNull(ReferralCode.fromInstallReferrer("ref=ABCDEFGHJ")) // 9 chars
        assertNull(ReferralCode.fromInstallReferrer("ref=ABCDEFG1")) // 1 is not in the alphabet
        assertNull(ReferralCode.fromInstallReferrer("ref=ABCDEFG0")) // nor is 0
        assertNull(ReferralCode.fromInstallReferrer("ref=ABC%20EFGH"))
    }

    @Test
    fun `isValid matches the server alphabet exactly`() {
        assertTrue(ReferralCode.isValid("ABCDEFGH"))
        assertTrue(ReferralCode.isValid("23456789"))
        assertFalse(ReferralCode.isValid("abcdefgh"))
        assertFalse(ReferralCode.isValid(null))
    }
}
