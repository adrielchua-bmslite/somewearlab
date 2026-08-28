package com.somewearlabs.gateway;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class TransportFragmentationPolicyTest {
    @Test
    public void oversizedRadioUsesSc3Framing() {
        assertTrue(TransportFragmentationPolicy.shouldFragment("Radio", 2));
    }

    @Test
    public void oversizedSatelliteRemainsOneNativeCompositeParent() {
        assertFalse(TransportFragmentationPolicy.shouldFragment("Satellite", 2));
        assertFalse(TransportFragmentationPolicy.shouldFragment("Satellite", 9));
    }

    @Test
    public void singleTransmissionPayloadIsNeverFragmented() {
        assertFalse(TransportFragmentationPolicy.shouldFragment("Radio", 1));
        assertFalse(TransportFragmentationPolicy.shouldFragment("Satellite", 1));
    }

    @Test
    public void rejectsInvalidTransmissionEstimate() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TransportFragmentationPolicy.shouldFragment("Satellite", 0)
        );
    }

    @Test
    public void everySatelliteParentAndNativeChildRequiresBackhaulAck() {
        assertTrue(TransportFragmentationPolicy.requiresBackhaulAck("Satellite"));
        assertFalse(TransportFragmentationPolicy.requiresBackhaulAck("Radio"));
    }
}
