package com.meerkly.android.proxy

import com.meerkly.sdk.ClientState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyStateTest {

    @Test
    fun `every SDK state maps to a UI state`() {
        assertEquals(ProxyState.Disconnected, ProxyState.from(ClientState.IDLE))
        assertEquals(ProxyState.Connecting, ProxyState.from(ClientState.CONNECTING))
        assertEquals(ProxyState.Connected, ProxyState.from(ClientState.CONNECTED))
        assertEquals(ProxyState.Stopped, ProxyState.from(ClientState.STOPPED))
    }

    @Test
    fun `the mapping is total`() {
        // A new ClientState variant in a future SDK must fail here rather than
        // silently rendering as whatever the last branch happened to be.
        ClientState.entries.forEach { ProxyState.from(it) }
    }

    @Test
    fun `only Connected earns`() {
        assertTrue(ProxyState.Connected.isEarning)
        listOf(
            ProxyState.Disconnected, ProxyState.Connecting,
            ProxyState.Stopped, ProxyState.Failed,
        ).forEach { assertFalse("$it must not claim to be earning", it.isEarning) }
    }
}
