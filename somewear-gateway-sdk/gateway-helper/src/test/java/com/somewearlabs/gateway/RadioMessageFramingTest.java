package com.somewearlabs.gateway;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class RadioMessageFramingTest {
    private static final String TRANSFER_ID = "0123456789abcdef";

    @Test
    public void splitAndReassemblePreservesJsonAndMessageIdOutOfOrder() {
        String messageId = "sc3-message-世界-001";
        String json = "{\"type\":\"position\",\"note\":\"hello 🌏\",\"values\":["
                + "1,2,3,4,5,6,7,8,9,10],\"padding\":\""
                + "x".repeat(900)
                + "\"}";
        List<String> frames = RadioMessageFraming.split(
                messageId,
                json,
                TRANSFER_ID,
                RadioMessageFraming.DEFAULT_CHUNK_BYTES
        );
        assertTrue(frames.size() > 1);
        Collections.reverse(frames);

        RadioMessageReassembler reassembler = new RadioMessageReassembler();
        RadioMessageReassembler.Result result = null;
        long now = 1_000L;
        for (String frame : frames) {
            result = reassembler.accept(frame, 75603L, "386912", "RADIO", now++);
        }

        assertNotNull(result);
        assertEquals(RadioMessageReassembler.Result.Kind.COMPLETE, result.kind);
        assertEquals(messageId, result.messageId);
        assertEquals(json, result.content);
        assertEquals(0, reassembler.activeCount());
    }

    @Test
    public void satelliteFramesPreserveRftAndCasSizedJson() {
        assertSatelliteRoundTrip("rft-message", jsonPayloadOfWireSize(504));
        assertSatelliteRoundTrip("cas-message", jsonPayloadOfWireSize(2171));
    }

    @Test
    public void duplicateFragmentIsIdempotent() {
        List<String> frames = RadioMessageFraming.split(
                "message-id",
                "z".repeat(600),
                TRANSFER_ID,
                RadioMessageFraming.DEFAULT_CHUNK_BYTES
        );
        RadioMessageReassembler reassembler = new RadioMessageReassembler();
        assertEquals(
                RadioMessageReassembler.Result.Kind.PENDING,
                reassembler.accept(frames.get(0), 1L, "sender", "RADIO", 1L).kind
        );
        assertEquals(
                RadioMessageReassembler.Result.Kind.PENDING,
                reassembler.accept(frames.get(0), 1L, "sender", "RADIO", 2L).kind
        );

        RadioMessageReassembler.Result result = null;
        for (int index = 1; index < frames.size(); index++) {
            result = reassembler.accept(frames.get(index), 1L, "sender", "RADIO", index + 2L);
        }
        assertNotNull(result);
        assertEquals(RadioMessageReassembler.Result.Kind.COMPLETE, result.kind);
        assertEquals("z".repeat(600), result.content);
    }

    @Test
    public void checksumFailureDoesNotEmitApplicationMessage() {
        List<String> frames = new ArrayList<>(RadioMessageFraming.split(
                "message-id",
                "a".repeat(500),
                TRANSFER_ID,
                RadioMessageFraming.DEFAULT_CHUNK_BYTES
        ));
        String last = frames.get(frames.size() - 1);
        frames.set(frames.size() - 1, last.substring(0, last.length() - 1) + "A");

        RadioMessageReassembler reassembler = new RadioMessageReassembler();
        RadioMessageReassembler.Result result = null;
        for (String frame : frames) {
            result = reassembler.accept(frame, 1L, "sender", "RADIO", 1L);
        }
        assertNotNull(result);
        assertEquals(RadioMessageReassembler.Result.Kind.INVALID, result.kind);
        assertEquals(0, reassembler.activeCount());
    }

    @Test
    public void ordinaryMessageIsNotClaimedByFramingLayer() {
        assertNull(RadioMessageFraming.parse("{\"type\":\"chat\",\"body\":\"hello\"}"));
        RadioMessageReassembler.Result result = new RadioMessageReassembler().accept(
                "small text",
                1L,
                "sender",
                "RADIO",
                1L
        );
        assertEquals(RadioMessageReassembler.Result.Kind.NOT_FRAME, result.kind);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOversizedTransportMessage() {
        RadioMessageFraming.split(
                "message-id",
                "x".repeat(RadioMessageFraming.MAX_WIRE_BYTES),
                TRANSFER_ID,
                RadioMessageFraming.DEFAULT_CHUNK_BYTES
        );
    }

    @Test
    public void parsedFrameCarriesExactChunkBytes() {
        List<String> frames = RadioMessageFraming.split(
                "id",
                "payload",
                TRANSFER_ID,
                RadioMessageFraming.MIN_CHUNK_BYTES
        );
        RadioMessageFraming.Frame frame = RadioMessageFraming.parse(frames.get(0));
        assertNotNull(frame);
        assertEquals(0, frame.index);
        assertEquals(frames.size(), frame.count);
        assertArrayEquals(frame.data, frame.data.clone());
    }

    private static void assertSatelliteRoundTrip(String messageId, String json) {
        assertEquals(json.length(), json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        List<String> frames = RadioMessageFraming.split(
                messageId,
                json,
                TRANSFER_ID,
                RadioMessageFraming.DEFAULT_CHUNK_BYTES
        );
        assertTrue(frames.size() > 1);

        RadioMessageReassembler reassembler = new RadioMessageReassembler();
        RadioMessageReassembler.Result result = null;
        for (int index = 0; index < frames.size(); index++) {
            result = reassembler.accept(
                    frames.get(index),
                    75603L,
                    "satellite-sender",
                    "SATELLITE",
                    2_000L + index
            );
        }

        assertNotNull(result);
        assertEquals(RadioMessageReassembler.Result.Kind.COMPLETE, result.kind);
        assertEquals(messageId, result.messageId);
        assertEquals(json, result.content);
        assertEquals(0, reassembler.activeCount());
    }

    private static String jsonPayloadOfWireSize(int size) {
        String prefix = "{\"data\":\"";
        String suffix = "\"}";
        return prefix + "x".repeat(size - prefix.length() - suffix.length()) + suffix;
    }
}
