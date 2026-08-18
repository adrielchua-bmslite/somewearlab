package com.sc3.somewear.sdk

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceObservationTest {
    @Test
    fun unchangedStatusIsDeduplicated() {
        val status = DeviceStatus(
            connectionState = ConnectionState.CONNECTED,
            operationState = OperationState.COMPLETED,
            operationResult = "Success",
            localTransport = LocalTransport.BLUETOOTH,
        )

        assertTrue(
            sameDeviceObservation(
                SomewearResult.Success(status),
                SomewearResult.Success(status.copy()),
            ),
        )
    }

    @Test
    fun actualConnectionChangeIsEmitted() {
        val connected = DeviceStatus(
            connectionState = ConnectionState.CONNECTED,
            operationState = OperationState.COMPLETED,
            operationResult = "Success",
            localTransport = LocalTransport.BLUETOOTH,
        )

        assertFalse(
            sameDeviceObservation(
                SomewearResult.Success(connected),
                SomewearResult.Success(
                    connected.copy(
                        connectionState = ConnectionState.DISCONNECTED,
                        localTransport = LocalTransport.NONE,
                    ),
                ),
            ),
        )
    }

    @Test
    fun repeatedEquivalentFailuresAreDeduplicated() {
        val first = SomewearResult.Failure(
            SomewearError(
                SomewearErrorCode.INTERNAL,
                "status unavailable",
                SomewearGatewayContract.Method.GET_DEVICE_STATUS,
                IllegalStateException("first cause"),
            ),
        )
        val second = SomewearResult.Failure(
            SomewearError(
                SomewearErrorCode.INTERNAL,
                "status unavailable",
                SomewearGatewayContract.Method.GET_DEVICE_STATUS,
                IllegalStateException("different instance"),
            ),
        )

        assertTrue(sameDeviceObservation(first, second))
    }
}
