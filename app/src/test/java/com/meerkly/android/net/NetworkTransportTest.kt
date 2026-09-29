package com.meerkly.android.net

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkTransportTest {

    @Test
    fun `a single transport is reported as itself`() {
        assertEquals("cellular", NetworkTransport.of(cellular = true, wifi = false, ethernet = false))
        assertEquals("wifi", NetworkTransport.of(cellular = false, wifi = true, ethernet = false))
        assertEquals("ethernet", NetworkTransport.of(cellular = false, wifi = false, ethernet = true))
    }

    /** Bluetooth tethering, USB, a VPN that hides what it runs over. */
    @Test
    fun `none of the three is other`() {
        assertEquals("other", NetworkTransport.of(cellular = false, wifi = false, ethernet = false))
    }

    /**
     * A VPN over several networks. Picking cellular would make the exit look
     * mobile on a guess; the gateway would rather hear it does not know.
     */
    @Test
    fun `more than one transport is other, never a guess at cellular`() {
        assertEquals("other", NetworkTransport.of(cellular = true, wifi = true, ethernet = false))
        assertEquals("other", NetworkTransport.of(cellular = true, wifi = false, ethernet = true))
    }
}
