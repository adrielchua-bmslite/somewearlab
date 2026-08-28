package com.somewearlabs.gateway;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.util.List;
import org.junit.Test;

public class FragmentRecoveryStoreTest {
    private static final String TRANSFER_ID = "0123456789abcdef";

    @Test
    public void outgoingFramesSurviveStoreReopenAndAckRemovesThem() throws Exception {
        java.io.File root = Files.createTempDirectory("sc3-fragment-outbox").toFile();
        List<String> frames = RadioMessageFraming.split(
                "message-1",
                "x".repeat(900),
                TRANSFER_ID,
                RadioMessageFraming.DEFAULT_CHUNK_BYTES
        );
        long now = 10_000L;
        FragmentRecoveryStore first = new FragmentRecoveryStore(root);
        FragmentRecoveryStore.OutgoingRecord written = first.saveOutgoing(
                TRANSFER_ID,
                "message-1",
                77L,
                frames,
                now
        );

        FragmentRecoveryStore reopened = new FragmentRecoveryStore(root);
        FragmentRecoveryStore.OutgoingRecord restored = reopened.findOutgoing(
                TRANSFER_ID,
                written.checksum,
                77L,
                now + 1L
        );

        assertNotNull(restored);
        assertEquals("message-1", restored.messageId);
        assertEquals(frames, restored.frames);
        assertTrue(reopened.acknowledge(TRANSFER_ID, written.checksum, 77L, now + 2L));
        assertNull(reopened.findOutgoing(TRANSFER_ID, written.checksum, 77L, now + 3L));
    }

    @Test
    public void incompleteTransferPersistsMissingIndexesAndCompletesOutOfOrder() throws Exception {
        java.io.File root = Files.createTempDirectory("sc3-fragment-inbox").toFile();
        List<String> frames = RadioMessageFraming.split(
                "message-2",
                "y".repeat(700),
                TRANSFER_ID,
                RadioMessageFraming.DEFAULT_CHUNK_BYTES
        );
        FragmentRecoveryStore store = new FragmentRecoveryStore(root);
        FragmentRecoveryStore.IncomingRecord first = store.recordIncoming(
                frames.get(0),
                88L,
                "sender",
                "RADIO",
                20_000L
        );
        store.recordIncoming(
                frames.get(frames.size() - 1),
                88L,
                "sender",
                "RADIO",
                20_001L
        );

        FragmentRecoveryStore reopened = new FragmentRecoveryStore(root);
        FragmentRecoveryStore.IncomingRecord partial = reopened.findIncoming(
                TRANSFER_ID,
                20_002L
        );
        assertNotNull(partial);
        assertEquals(frames.size() - 2, partial.missingIndexes().size());
        assertFalse(partial.isComplete());

        for (Integer index : partial.missingIndexes()) {
            reopened.recordIncoming(
                    frames.get(index),
                    88L,
                    "sender",
                    "RADIO",
                    20_010L + index
            );
        }
        FragmentRecoveryStore.IncomingRecord complete = reopened.findIncoming(
                TRANSFER_ID,
                21_000L
        );
        assertNotNull(complete);
        assertTrue(complete.isComplete());
        assertEquals(frames.size(), complete.receivedCount());
        assertEquals(first.checksum, complete.checksum);
        assertEquals(List.of(), reopened.listIncoming(21_000L));

        FragmentRecoveryStore afterRestart = new FragmentRecoveryStore(root);
        FragmentRecoveryStore.IncomingRecord tombstone = afterRestart.findIncoming(
                TRANSFER_ID,
                88L,
                "sender",
                "RADIO",
                21_001L
        );
        assertNotNull(tombstone);
        assertTrue(tombstone.isComplete());
    }

    @Test
    public void expiredRecordsAreRemoved() throws Exception {
        java.io.File root = Files.createTempDirectory("sc3-fragment-expiry").toFile();
        List<String> frames = RadioMessageFraming.split(
                "message-3",
                "z".repeat(500),
                TRANSFER_ID,
                RadioMessageFraming.DEFAULT_CHUNK_BYTES
        );
        FragmentRecoveryStore store = new FragmentRecoveryStore(root);
        FragmentRecoveryStore.OutgoingRecord outgoing = store.saveOutgoing(
                TRANSFER_ID,
                "message-3",
                99L,
                frames,
                1L
        );
        store.recordIncoming(frames.get(0), 99L, "sender", "RADIO", 1L);

        long expiredAt = FragmentRecoveryStore.RECORD_TTL_MS + 2L;
        store.prune(expiredAt);

        assertNull(store.findOutgoing(TRANSFER_ID, outgoing.checksum, 99L, expiredAt));
        assertEquals(List.of(), store.listIncoming(expiredAt));
    }
}
