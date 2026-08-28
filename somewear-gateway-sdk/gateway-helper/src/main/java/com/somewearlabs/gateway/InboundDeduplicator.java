package com.somewearlabs.gateway;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded cross-channel duplicate suppression for SC3-owned message/file IDs. */
final class InboundDeduplicator {
    private final int maximumEntries;
    private final long ttlMillis;
    private final LinkedHashMap<String, Long> seen = new LinkedHashMap<>();

    InboundDeduplicator(int maximumEntries, long ttlMillis) {
        if (maximumEntries < 1) throw new IllegalArgumentException("maximumEntries must be positive");
        if (ttlMillis < 1L) throw new IllegalArgumentException("ttlMillis must be positive");
        this.maximumEntries = maximumEntries;
        this.ttlMillis = ttlMillis;
    }

    synchronized boolean firstSeen(String key, long now) {
        expire(now);
        if (seen.containsKey(key)) return false;
        seen.put(key, now);
        while (seen.size() > maximumEntries) {
            Iterator<String> keys = seen.keySet().iterator();
            if (!keys.hasNext()) break;
            keys.next();
            keys.remove();
        }
        return true;
    }

    synchronized boolean hasSeen(String key, long now) {
        expire(now);
        return seen.containsKey(key);
    }

    synchronized void clear() {
        seen.clear();
    }

    private void expire(long now) {
        Iterator<Map.Entry<String, Long>> entries = seen.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<String, Long> entry = entries.next();
            if (now - entry.getValue() > ttlMillis) entries.remove();
        }
    }
}
