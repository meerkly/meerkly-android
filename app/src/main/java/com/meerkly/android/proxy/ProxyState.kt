package com.meerkly.android.proxy

import com.meerkly.sdk.ClientState

/**
 * Where the exit-node client stands, as the UI and the ongoing notification
 * need it.
 *
 * This is not just an alias for the SDK's [ClientState]: it adds [Failed],
 * which the SDK expresses by throwing rather than by a state, and it is what
 * lets the rest of the app depend on one enum instead of on the SDK's surface.
 */
enum class ProxyState {
    /** Never started, or the client has been torn down. */
    Disconnected,

    /** Connecting and registering. */
    Connecting,

    /** Registered with the gateway — the only state that earns. */
    Connected,

    /** Stopped on purpose. */
    Stopped,

    /** start() threw. [ProxyController.lastError] carries the reason. */
    Failed,
    ;

    /** True only while the device can actually be given traffic. */
    val isEarning: Boolean get() = this == Connected

    companion object {
        /**
         * Deliberately exhaustive over [ClientState] with no `else` branch: a
         * new variant in a future SDK becomes a compile error here, which is
         * where the decision belongs, rather than quietly rendering as
         * whatever the last branch was.
         */
        fun from(state: ClientState): ProxyState = when (state) {
            ClientState.IDLE -> Disconnected
            ClientState.CONNECTING -> Connecting
            ClientState.CONNECTED -> Connected
            ClientState.STOPPED -> Stopped
        }
    }
}
