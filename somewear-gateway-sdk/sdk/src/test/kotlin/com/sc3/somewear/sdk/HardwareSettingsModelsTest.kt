package com.sc3.somewear.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class HardwareSettingsModelsTest {
    @Test
    fun symmetricTrackingIntervalUsesGpsValueForSending() {
        assertEquals(15, TrackingInterval(15).sendingSeconds)
    }

    @Test
    fun trackingIntervalsMustBePositive() {
        expectIllegalArgument { TrackingInterval(0) }
        expectIllegalArgument { TrackingInterval(5, 0) }
    }

    @Test
    fun radioFrequenciesMustBePositive() {
        expectIllegalArgument { RadioChannel(0, 915_000_000) }
        expectIllegalArgument { RadioChannel(915_000_000, -1) }
    }

    private fun expectIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
