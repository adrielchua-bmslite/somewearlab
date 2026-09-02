package com.somewearlabs.gateway;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class IncomingMessageStoreTest {
    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void completedMessagesSurviveReopenAndAcknowledgement() throws Exception {
        File root = temporaryFolder.newFolder("inbox");
        IncomingMessageStore first = new IncomingMessageStore(root, 20);
        IncomingMessageStore.Record one = first.append(
                "one", "{\"kind\":\"BFT\"}", 17L, "41", "SATELLITE", 100L
        );
        IncomingMessageStore.Record two = first.append(
                "two", "{\"kind\":\"RFT\"}", 17L, "42", "RADIO", 200L
        );

        IncomingMessageStore reopened = new IncomingMessageStore(root, 20);
        List<IncomingMessageStore.Record> replayed = reopened.listAfter(0L, 20);
        assertEquals(2, replayed.size());
        assertEquals(one.sequence, replayed.get(0).sequence);
        assertEquals("{\"kind\":\"RFT\"}", replayed.get(1).content);

        IncomingMessageStore.Stats acknowledged = reopened.acknowledgeThrough(one.sequence);
        assertEquals(one.sequence, acknowledged.acknowledgedThroughSequence);
        assertEquals(1, acknowledged.queuedCount);

        IncomingMessageStore afterAckReopen = new IncomingMessageStore(root, 20);
        List<IncomingMessageStore.Record> remaining = afterAckReopen.listAfter(0L, 20);
        assertEquals(1, remaining.size());
        assertEquals(two.sequence, remaining.get(0).sequence);
        assertTrue(afterAckReopen.append(
                "three", "next", 17L, null, "SATELLITE", 300L
        ).sequence > two.sequence);
    }

    @Test
    public void storesContentLargerThanDataOutputUtfLimit() throws Exception {
        File root = temporaryFolder.newFolder("large");
        IncomingMessageStore store = new IncomingMessageStore(root, 20);
        String content = "x".repeat(100_000);
        store.append("large-json", content, 1L, null, "SATELLITE", 10L);

        assertEquals(content, new IncomingMessageStore(root, 20)
                .listAfter(0L, 1)
                .get(0)
                .content);
    }

    @Test
    public void boundedStoreReportsForcedDropsInsteadOfSilentlyReplayingThem() throws Exception {
        File root = temporaryFolder.newFolder("bounded");
        IncomingMessageStore store = new IncomingMessageStore(root, 2);
        IncomingMessageStore.Record first = store.append(
                "first", "1", 1L, null, "RADIO", 1L
        );
        store.append("second", "2", 1L, null, "RADIO", 2L);
        store.append("third", "3", 1L, null, "RADIO", 3L);

        IncomingMessageStore.Stats stats = store.stats();
        assertEquals(2, stats.queuedCount);
        assertEquals(1L, stats.droppedCount);
        assertEquals(first.sequence, stats.acknowledgedThroughSequence);
        assertEquals("second", store.listAfter(0L, 10).get(0).messageId);
    }

    @Test
    public void rejectsAcknowledgingSequencesThatHaveNotBeenAllocated() throws Exception {
        IncomingMessageStore store = new IncomingMessageStore(
                temporaryFolder.newFolder("future"),
                20
        );
        IncomingMessageStore.Record record = store.append(
                "one", "content", 1L, null, "RADIO", 1L
        );
        try {
            store.acknowledgeThrough(record.sequence + 1L);
            fail("Expected future acknowledgement to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("future"));
        }
    }

    @Test
    public void secondStartupHandleReconcilesRecordsCreatedAfterItOpened() throws Exception {
        File root = temporaryFolder.newFolder("startup-race");
        IncomingMessageStore providerHandle = new IncomingMessageStore(root, 20);
        IncomingMessageStore serviceHandle = new IncomingMessageStore(root, 20);
        IncomingMessageStore.Record record = providerHandle.append(
                "startup-race", "content", 1L, null, "SATELLITE", 1L
        );

        IncomingMessageStore.Stats stats = serviceHandle.stats();
        assertEquals(record.sequence, stats.latestSequence);
        assertEquals(1, stats.queuedCount);
        assertEquals(
                record.sequence,
                serviceHandle.acknowledgeThrough(record.sequence)
                        .acknowledgedThroughSequence
        );
    }
}
