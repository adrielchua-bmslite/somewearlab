package com.sc3.somewear.sdk

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryObservationTest {
    @Test
    fun sampleTimeAloneDoesNotReemitTelemetry() {
        val first = telemetry(sampledAt = 1L)
        val second = telemetry(sampledAt = 2L)

        assertTrue(
            sameNodeTelemetryObservation(
                SomewearResult.Success(first),
                SomewearResult.Success(second),
            ),
        )
    }

    @Test
    fun changedSatelliteQualityIsEmitted() {
        assertFalse(
            sameNodeTelemetryObservation(
                SomewearResult.Success(telemetry(sampledAt = 1L)),
                SomewearResult.Success(
                    telemetry(sampledAt = 2L).copy(
                        satelliteQuality = 3,
                        satelliteSendable = true,
                    ),
                ),
            ),
        )
    }

    @Test
    fun meshSampleTimeAloneDoesNotReemit() {
        val first = MeshNetworkStatus(false, null, null, null, null, false, null, 1L)
        val second = first.copy(sampledAtEpochMillis = 2L)

        assertTrue(
            sameMeshNetworkObservation(
                SomewearResult.Success(first),
                SomewearResult.Success(second),
            ),
        )
    }

    private fun telemetry(sampledAt: Long): NodeTelemetry = NodeTelemetry(
        batteryPercent = 80,
        chargeStatus = "ChargeStatusNone",
        powerStatus = "PowerStatusOn",
        activityState = "Idle",
        satelliteQuality = 1,
        satelliteSendable = false,
        firmwareVersion = "1.2.3",
        networkVersion = "1.2.3",
        hardwareFlavor = "Node",
        serialNumber = "serial",
        imei = "imei",
        gpsInitialFix = true,
        trackingState = "Active",
        trackingEnabled = true,
        lowBandwidthMultiplier = 1,
        wakeAtEpochMillis = null,
        sampledAtEpochMillis = sampledAt,
    )
}
