package com.somewearlabs.gateway;

/** Selects the layer responsible for splitting an oversized low-speed payload. */
final class TransportFragmentationPolicy {
    private TransportFragmentationPolicy() {}

    static boolean shouldFragment(String channel, int estimatedTransmissionCount) {
        if (estimatedTransmissionCount < 1) {
            throw new IllegalArgumentException("estimatedTransmissionCount must be positive");
        }
        // The retained core only reassembles its native composite Part payloads on Satellite.
        // Radio therefore needs SC3 framing; Satellite must remain one parent MessagePayload.
        return "Radio".equals(channel) && estimatedTransmissionCount > 1;
    }

    static boolean requiresBackhaulAck(String channel) {
        return "Satellite".equals(channel);
    }
}
