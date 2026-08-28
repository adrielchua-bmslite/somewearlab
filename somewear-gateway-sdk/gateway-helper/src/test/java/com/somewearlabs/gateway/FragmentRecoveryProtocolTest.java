package com.somewearlabs.gateway;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class FragmentRecoveryProtocolTest {
    @Test
    public void requestRoundTripSortsAndDeduplicatesIndexes() {
        String encoded = FragmentRecoveryProtocol.encodeRequest(
                "0123456789abcdef",
                0xfeedbeefL,
                List.of(7, 2, 7, 0)
        );

        FragmentRecoveryProtocol.Control parsed = FragmentRecoveryProtocol.parse(encoded);

        assertEquals(FragmentRecoveryProtocol.Control.Kind.REQUEST, parsed.kind);
        assertEquals("0123456789abcdef", parsed.transferId);
        assertEquals(0xfeedbeefL, parsed.checksum);
        assertEquals(List.of(0, 2, 7), parsed.indexes);
    }

    @Test
    public void acknowledgementRoundTrips() {
        String encoded = FragmentRecoveryProtocol.encodeAck("0123456789abcdef", 42L);
        FragmentRecoveryProtocol.Control parsed = FragmentRecoveryProtocol.parse(encoded);

        assertEquals(FragmentRecoveryProtocol.Control.Kind.ACK, parsed.kind);
        assertEquals(42L, parsed.checksum);
        assertEquals(List.of(), parsed.indexes);
    }

    @Test
    public void batchesBoundEveryControlMessage() {
        List<Integer> indexes = new ArrayList<>();
        for (int index = 0; index < 61; index++) indexes.add(index);

        List<List<Integer>> batches = FragmentRecoveryProtocol.batches(indexes);

        assertEquals(3, batches.size());
        assertEquals(24, batches.get(0).size());
        assertEquals(24, batches.get(1).size());
        assertEquals(13, batches.get(2).size());
    }

    @Test
    public void ordinaryApplicationMessageIsNotClaimed() {
        assertNull(FragmentRecoveryProtocol.parse("{\"type\":\"RFT\"}"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsEmptyRecoveryRequest() {
        FragmentRecoveryProtocol.encodeRequest("0123456789abcdef", 1L, List.of());
    }
}
