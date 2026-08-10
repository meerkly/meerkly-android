package com.meerkly.android.gateway

/**
 * Where the worker's gateway WebSocket actually stands. The dashboard renders
 * this rather than assuming a signed-in user is an earning user — being paired
 * says nothing about whether the socket is up.
 *
 * [Connected] deliberately means "the gateway ACKED our register", not "TCP is
 * up": the gateway only places a worker in its dispatch pool after the register
 * frame is verified, so an open-but-unregistered socket earns nothing and must
 * not be reported as connected.
 */
enum class WorkerConnection {
    /** No GATEWAY_URL compiled in — this build never connects. */
    Disabled,

    /** Not started yet, or deliberately stopped. */
    Disconnected,

    /** Opening the socket. */
    Connecting,

    /** Socket open, register sent, waiting for the gateway's ack. */
    Registering,

    /** Registered and in the dispatch pool — the only state that earns. */
    Connected,

    /** Last attempt failed; retrying with backoff. */
    Offline,

    /** Terminal auth rejection (device_auth_failed): this device needs pairing
     *  before reconnecting is worth attempting. */
    Unpaired,
    ;

    /** True while the worker can actually be handed crawl jobs. */
    val isEarning: Boolean get() = this == Connected
}
