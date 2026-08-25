package com.somewearlabs.gateway;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InboundDeduplicatorTest {
    @Test
    public void suppressesCrossChannelDuplicateUntilExpiry() {
        InboundDeduplicator deduplicator = new InboundDeduplicator(10, 100L);

        assertTrue(deduplicator.firstSeen("workspace/sender/message", 1_000L));
        assertFalse(deduplicator.firstSeen("workspace/sender/message", 1_050L));
        assertTrue(deduplicator.firstSeen("workspace/sender/message", 1_101L));
    }

    @Test
    public void evictsOldestAtCapacity() {
        InboundDeduplicator deduplicator = new InboundDeduplicator(2, 10_000L);

        assertTrue(deduplicator.firstSeen("one", 1L));
        assertTrue(deduplicator.firstSeen("two", 2L));
        assertTrue(deduplicator.firstSeen("three", 3L));
        assertTrue(deduplicator.firstSeen("one", 4L));
    }
}
