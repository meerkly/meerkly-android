package com.meerkly.android.proxy

import org.junit.Assert.assertEquals
import org.junit.Test

class RefusalTest {
    @Test
    fun `the gateway's one-device-per-address refusal gets its own copy`() {
        assertEquals(
            Refusal.SharedConnection,
            Refusal.of("another device is already connected from this IP address"),
        )
    }

    @Test
    fun `any other reason is shown as the gateway said it`() {
        assertEquals(Refusal.Other, Refusal.of("unknown publisher id"))
    }
}
