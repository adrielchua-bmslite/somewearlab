package com.sc3.somewear.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BluetoothAddressTest {
    @Test
    fun normalizesWhitespaceAndCase() {
        assertEquals(
            "AA:BB:CC:DD:EE:FF",
            normalizeBluetoothMacAddress("  aa:bb:cc:dd:ee:ff  "),
        )
    }

    @Test
    fun rejectsMalformedAddress() {
        assertNull(normalizeBluetoothMacAddress("AA-BB-CC-DD-EE-FF"))
        assertNull(normalizeBluetoothMacAddress("not-a-mac"))
        assertNull(normalizeBluetoothMacAddress(""))
    }
}
