package com.meerkly.android.proxy

/**
 * What a gateway refusal means for the person holding the phone.
 *
 * The gateway explains every refusal in words (see
 * [ProxyController.rejection]). One of them is common enough, and fixable
 * enough, to deserve its own copy: the gateway admits one device per internet
 * connection, so a phone on the same Wi-Fi as a laptop already sharing is
 * turned away. Anything else is shown as the gateway said it.
 */
enum class Refusal {
    /** Another device already shares from this IP address. */
    SharedConnection,

    /** Any other reason; show the gateway's own words. */
    Other,
    ;

    companion object {
        /**
         * Matched on the gateway's wording (meerkly crates/gateway/src/quic.rs),
         * which is fixed English, not localised. If that text changes, this
         * falls back to [Other] and the raw reason is still shown.
         */
        private const val SHARED_CONNECTION = "already connected from this ip address"

        fun of(reason: String): Refusal =
            if (reason.lowercase().contains(SHARED_CONNECTION)) SharedConnection else Other
    }
}
