package com.sc3.somewear.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Test

class Sc3MessageEnvelopeTest {
    @Test
    fun `encodes a compact versioned envelope`() {
        val envelope = Sc3MessageEnvelope(
            type = "position",
            sender = "SC3-01",
            bodyJson = "{\"lat\":1.3521,\"lon\":103.8198}",
            id = "message-1",
            sentAtEpochMillis = 1234L,
        )

        assertEquals(
            "{\"v\":1,\"id\":\"message-1\",\"type\":\"position\",\"sentAt\":1234," +
                "\"sender\":\"SC3-01\",\"target\":\"workspace\"," +
                "\"body\":{\"lat\":1.3521,\"lon\":103.8198}}",
            envelope.encode(),
        )
    }

    @Test
    fun `escapes application strings`() {
        val encoded = Sc3MessageEnvelope(
            type = "chat\nmessage",
            sender = "SC3-\"01\"",
            bodyJson = "{}",
            id = "id",
            sentAtEpochMillis = 1L,
        ).encode()

        assertTrue(encoded.contains("\"type\":\"chat\\nmessage\""))
        assertTrue(encoded.contains("\"sender\":\"SC3-\\\"01\\\"\""))
    }

    @Test
    fun `route policies use the gateway wire values`() {
        assertEquals("RADIO_ONLY", RoutePolicy.RADIO_ONLY.wireValue)
        assertEquals("RADIO_THEN_SATELLITE", RoutePolicy.RADIO_THEN_SATELLITE.wireValue)
        assertEquals("SATELLITE_ONLY", RoutePolicy.SATELLITE_ONLY.wireValue)
    }

    @Test
    fun `satellite gets an independent five minute timeout by default`() {
        val request = SendRequest(workspaceId = 1L, content = "test")

        assertEquals(30_000L, request.radioTimeoutMillis)
        assertEquals(300_000L, request.satelliteTimeoutMillis)
    }

    @Test
    fun `fallback message id stays within gateway framing bound`() {
        try {
            SendRequest(workspaceId = 1L, content = "test", messageId = "x".repeat(4_097))
            fail("Expected the oversized messageId to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun `workspace models expose selection and key readiness separately`() {
        val workspace = WorkspaceInfo(
            id = 42L,
            name = "Operations",
            ready = true,
            active = false,
            member = true,
            meshKeyInstalled = true,
        )

        assertTrue(workspace.member)
        assertTrue(workspace.meshKeyInstalled)
        assertTrue(!workspace.active)
    }
}
