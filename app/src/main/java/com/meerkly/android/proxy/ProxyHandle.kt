package com.meerkly.android.proxy

import com.meerkly.sdk.ClientState
import com.meerkly.sdk.ProxyClient
import com.meerkly.sdk.ProxyConfig

/**
 * The slice of the SDK client [ProxyController] actually uses.
 *
 * It exists to make the controller testable. `ProxyClient`'s constructor loads
 * libmeerkly.so through JNA, which a JVM unit test has no way to provide — so
 * without a seam here, none of the controller's logic could be tested off a
 * device. Production passes [SdkProxyHandle]; tests pass a fake.
 */
internal interface ProxyHandle {
    fun state(): ClientState
    fun clientKey(): String?
    suspend fun start()
    suspend fun stop()
    fun destroy()
}

/** The real thing. A pass-through; it deliberately holds no logic of its own. */
internal class SdkProxyHandle(config: ProxyConfig) : ProxyHandle {
    private val client = ProxyClient(config)

    override fun state(): ClientState = client.state()
    override fun clientKey(): String? = client.clientKey()
    override suspend fun start() = client.start()
    override suspend fun stop() = client.stop()
    override fun destroy() = client.destroy()
}
