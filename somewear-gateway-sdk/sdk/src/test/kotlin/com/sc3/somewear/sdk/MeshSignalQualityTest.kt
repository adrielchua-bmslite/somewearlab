package com.sc3.somewear.sdk

import org.junit.Assert.assertEquals
import org.junit.Test

class MeshSignalQualityTest {
    @Test
    fun mapsSomewearMeshRssiBoundaries() {
        assertEquals(MeshSignalQuality.UNKNOWN, MeshSignalQuality.fromRssi(null))
        assertEquals(MeshSignalQuality.UNKNOWN, MeshSignalQuality.fromRssi(-1))
        assertEquals(MeshSignalQuality.UNKNOWN, MeshSignalQuality.fromRssi(99))
        assertEquals(MeshSignalQuality.FAR, MeshSignalQuality.fromRssi(100))
        assertEquals(MeshSignalQuality.FAR, MeshSignalQuality.fromRssi(147))
        assertEquals(MeshSignalQuality.SOMEWHAT_CLOSE, MeshSignalQuality.fromRssi(148))
        assertEquals(MeshSignalQuality.SOMEWHAT_CLOSE, MeshSignalQuality.fromRssi(213))
        assertEquals(MeshSignalQuality.CLOSE, MeshSignalQuality.fromRssi(214))
    }

    @Test
    fun meshStatusExposesDerivedSignalQuality() {
        val status = MeshNetworkStatus(
            available = true,
            peerUserId = 42L,
            nextHopUserId = 42L,
            hopsAway = 1,
            signalRssi = 180,
            canBackhaulData = false,
            updatedAtEpochMillis = 1L,
            sampledAtEpochMillis = 2L,
        )

        assertEquals(MeshSignalQuality.SOMEWHAT_CLOSE, status.signalQuality)
    }
}
