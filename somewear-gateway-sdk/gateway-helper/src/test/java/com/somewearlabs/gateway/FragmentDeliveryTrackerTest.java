package com.somewearlabs.gateway;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class FragmentDeliveryTrackerTest {
    @Test
    public void reportsDeliveredOnlyAfterEveryFragmentIsDelivered() {
        FragmentDeliveryTracker tracker = new FragmentDeliveryTracker();
        tracker.register("message", "Radio", Arrays.asList(10, 11, 12));

        FragmentDeliveryTracker.Update first = tracker.update(
                10, "Delivered", "Radio", null, 1L
        );
        assertNotNull(first);
        assertFalse("DELIVERED".equals(first.status));

        FragmentDeliveryTracker.Update second = tracker.update(
                11, "Delivered", "Radio", null, 2L
        );
        assertNotNull(second);
        assertFalse("DELIVERED".equals(second.status));

        FragmentDeliveryTracker.Update third = tracker.update(
                12, "Delivered", "Radio", null, 3L
        );
        assertNotNull(third);
        assertEquals("DELIVERED", third.status);
        assertEquals("RADIO", third.channel);
        assertEquals(3, third.fragmentCount);
    }

    @Test
    public void fragmentFailureFailsOriginalMessageWithPosition() {
        FragmentDeliveryTracker tracker = new FragmentDeliveryTracker();
        tracker.register("message", "Radio", Arrays.asList(20, 21, 22));

        FragmentDeliveryTracker.Update update = tracker.update(
                21,
                "Error",
                "Radio",
                "ChannelDisabled",
                5L
        );
        assertNotNull(update);
        assertEquals("ERROR", update.status);
        assertTrue(update.errorReason.contains("Fragment 2/3"));
        assertTrue(update.errorReason.contains("ChannelDisabled"));
        assertNull(tracker.update(20, "Delivered", "Radio", null, 6L));
    }

    @Test
    public void mapsVendorProgressStatesToSdkStates() {
        FragmentDeliveryTracker tracker = new FragmentDeliveryTracker();
        tracker.register("message", "Radio", Arrays.asList(30, 31));

        assertEquals(
                "PENDING_TRANSFER",
                tracker.update(30, "Connecting", "None", null, 1L).status
        );
        assertEquals(
                "TRANSFERRING",
                tracker.update(31, "Sending", "Radio", null, 2L).status
        );
    }

    @Test
    public void ignoresUnknownParcel() {
        FragmentDeliveryTracker tracker = new FragmentDeliveryTracker();
        assertNull(tracker.update(404, "Error", "Satellite", "Unknown", 1L));
    }

    @Test
    public void cancellationReturnsEveryParcelAndBecomesTerminal() {
        FragmentDeliveryTracker tracker = new FragmentDeliveryTracker();
        tracker.register("message", "Radio", Arrays.asList(40, 41, 42));

        FragmentDeliveryTracker.Cancellation cancellation = tracker.cancel("message", 8L);

        assertNotNull(cancellation);
        assertEquals(Arrays.asList(40, 41, 42), cancellation.parcelIds);
        assertEquals("CANCELED", cancellation.update.status);
        assertEquals(3, cancellation.update.fragmentCount);
        assertNull(tracker.update(40, "Delivered", "Radio", null, 9L));
        assertNull(tracker.cancel("message", 10L));
    }
}
