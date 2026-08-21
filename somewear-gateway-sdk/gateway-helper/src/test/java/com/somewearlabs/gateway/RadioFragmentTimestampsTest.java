package com.somewearlabs.gateway;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class RadioFragmentTimestampsTest {
    @Test
    public void reservesOneDistinctSecondPerFragment() {
        RadioFragmentTimestamps.Reservation reservation =
                RadioFragmentTimestamps.reserve(1_700_000_000_456L, 0L, 6);

        assertEquals(1_700_000_001L, reservation.firstEpochSecond);
        assertEquals(1_700_000_006L, reservation.lastEpochSecond);
        for (int index = 1; index < reservation.count; index++) {
            assertEquals(
                    1_000L,
                    reservation.timestampMillis(index)
                            - reservation.timestampMillis(index - 1)
            );
        }
    }

    @Test
    public void restartContinuesAfterPersistedReservation() {
        RadioFragmentTimestamps.Reservation first =
                RadioFragmentTimestamps.reserve(1_700_000_000_456L, 0L, 512);
        RadioFragmentTimestamps.Reservation afterRestart =
                RadioFragmentTimestamps.reserve(
                        1_700_000_002_000L,
                        first.lastEpochSecond,
                        6
                );

        assertEquals(first.lastEpochSecond + 1L, afterRestart.firstEpochSecond);
        assertTrue(afterRestart.firstEpochSecond > first.firstEpochSecond);
    }

    @Test
    public void laterWallClockCatchesUpWithoutReusingSeconds() {
        RadioFragmentTimestamps.Reservation reservation =
                RadioFragmentTimestamps.reserve(1_700_001_000_000L, 1_700_000_100L, 2);

        assertEquals(1_700_001_001L, reservation.firstEpochSecond);
        assertEquals(1_700_001_002L, reservation.lastEpochSecond);
    }

    @Test
    public void rejectsInvalidFragmentCounts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RadioFragmentTimestamps.reserve(0L, 0L, 0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> RadioFragmentTimestamps.reserve(
                        0L,
                        0L,
                        RadioMessageFraming.MAX_FRAGMENTS + 1
                )
        );
    }
}
