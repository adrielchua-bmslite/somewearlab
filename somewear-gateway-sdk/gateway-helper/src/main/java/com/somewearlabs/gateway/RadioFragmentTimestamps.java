package com.somewearlabs.gateway;

/** Allocates whole-second timestamps that remain unique across gateway restarts. */
final class RadioFragmentTimestamps {
    private RadioFragmentTimestamps() {}

    static Reservation reserve(long nowMillis, long persistedLastEpochSecond, int count) {
        if (count < 1 || count > RadioMessageFraming.MAX_FRAGMENTS) {
            throw new IllegalArgumentException("Invalid radio fragment count");
        }
        long nowEpochSecond = Math.floorDiv(nowMillis, 1_000L);
        long firstEpochSecond = Math.max(
                Math.addExact(nowEpochSecond, 1L),
                Math.addExact(persistedLastEpochSecond, 1L)
        );
        long lastEpochSecond = Math.addExact(firstEpochSecond, count - 1L);
        return new Reservation(firstEpochSecond, lastEpochSecond, count);
    }

    static final class Reservation {
        final long firstEpochSecond;
        final long lastEpochSecond;
        final int count;

        Reservation(long firstEpochSecond, long lastEpochSecond, int count) {
            this.firstEpochSecond = firstEpochSecond;
            this.lastEpochSecond = lastEpochSecond;
            this.count = count;
        }

        long timestampMillis(int index) {
            if (index < 0 || index >= count) {
                throw new IndexOutOfBoundsException("Radio fragment index out of range");
            }
            return Math.multiplyExact(firstEpochSecond + index, 1_000L);
        }
    }
}
