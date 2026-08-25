package com.somewearlabs.gateway;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class RouteFallbackCoordinatorTest {
    @Test
    public void deliveryBeforeTimeoutSuppressesSatellite() {
        RouteFallbackCoordinator<String> coordinator = new RouteFallbackCoordinator<>();
        RouteFallbackCoordinator.Registration registration = coordinator.register(
                "message", Arrays.asList(1, 2), "satellite"
        );
        coordinator.arm("message", registration.generation);

        assertNull(coordinator.onRadioStatus("message", "Delivered"));
        assertFalse(coordinator.isPending("message"));
        assertNull(coordinator.onTimeout("message", registration.generation));
    }

    @Test
    public void timeoutClaimsExactlyOneSatelliteAttempt() {
        RouteFallbackCoordinator<String> coordinator = new RouteFallbackCoordinator<>();
        RouteFallbackCoordinator.Registration registration = coordinator.register(
                "message", Arrays.asList(3, 4), "satellite"
        );
        coordinator.arm("message", registration.generation);

        RouteFallbackCoordinator.Decision<String> decision = coordinator.onTimeout(
                "message", registration.generation
        );
        assertNotNull(decision);
        assertEquals(Arrays.asList(3, 4), decision.radioParcelIds);
        assertEquals("satellite", decision.satelliteAttempt);
        assertNull(coordinator.onTimeout("message", registration.generation));
        assertNull(coordinator.onRadioStatus("message", "Error"));
    }

    @Test
    public void radioErrorFallsBackImmediately() {
        RouteFallbackCoordinator<String> coordinator = new RouteFallbackCoordinator<>();
        RouteFallbackCoordinator.Registration registration = coordinator.register(
                "message", Arrays.asList(5), "satellite"
        );
        coordinator.arm("message", registration.generation);

        assertNotNull(coordinator.onRadioStatus("message", "ERROR"));
        assertFalse(coordinator.isPending("message"));
    }

    @Test
    public void retainedCoreCancellationAndCollapseTriggerFallback() {
        RouteFallbackCoordinator<String> coordinator = new RouteFallbackCoordinator<>();
        RouteFallbackCoordinator.Registration canceled = coordinator.register(
                "canceled", Arrays.asList(6), "satellite"
        );
        RouteFallbackCoordinator.Registration collapsed = coordinator.register(
                "collapsed", Arrays.asList(7), "satellite"
        );
        coordinator.arm("canceled", canceled.generation);
        coordinator.arm("collapsed", collapsed.generation);

        assertNotNull(coordinator.onRadioStatus("canceled", "Canceled"));
        assertNotNull(coordinator.onRadioStatus("collapsed", "Collapsed"));
        assertFalse(coordinator.isPending("canceled"));
        assertFalse(coordinator.isPending("collapsed"));
    }

    @Test
    public void staleTimerCannotClaimReplacementAttempt() {
        RouteFallbackCoordinator<String> coordinator = new RouteFallbackCoordinator<>();
        RouteFallbackCoordinator.Registration old = coordinator.register(
                "message", Arrays.asList(8), "old"
        );
        RouteFallbackCoordinator.Registration replacement = coordinator.register(
                "message", Arrays.asList(9), "new"
        );
        coordinator.arm("message", replacement.generation);

        assertNull(coordinator.onTimeout("message", old.generation));
        assertTrue(coordinator.isPending("message"));
        assertEquals(
                "new",
                coordinator.onTimeout("message", replacement.generation).satelliteAttempt
        );
    }

    @Test
    public void explicitCancelWinsAgainstTimer() {
        RouteFallbackCoordinator<String> coordinator = new RouteFallbackCoordinator<>();
        RouteFallbackCoordinator.Registration registration = coordinator.register(
                "message", Arrays.asList(10), "satellite"
        );

        coordinator.cancel("message");

        assertNull(coordinator.onTimeout("message", registration.generation));
        assertNull(coordinator.onRadioStatus("message", "Error"));
    }

    @Test
    public void timeoutAndTerminalErrorRaceStillProduceOneFallback() throws Exception {
        RouteFallbackCoordinator<String> coordinator = new RouteFallbackCoordinator<>();
        RouteFallbackCoordinator.Registration registration = coordinator.register(
                "message", Arrays.asList(11, 12), "satellite"
        );
        coordinator.arm("message", registration.generation);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger decisions = new AtomicInteger();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        workers.submit(() -> {
            start.await();
            if (coordinator.onTimeout("message", registration.generation) != null) {
                decisions.incrementAndGet();
            }
            return null;
        });
        workers.submit(() -> {
            start.await();
            if (coordinator.onRadioStatus("message", "Error") != null) {
                decisions.incrementAndGet();
            }
            return null;
        });

        start.countDown();
        workers.shutdown();
        assertTrue(workers.awaitTermination(2, TimeUnit.SECONDS));
        assertEquals(1, decisions.get());
        assertFalse(coordinator.isPending("message"));
    }

    @Test
    public void cancellationBeforeArmWinsWithoutSatellite() {
        RouteFallbackCoordinator<String> coordinator = new RouteFallbackCoordinator<>();
        RouteFallbackCoordinator.Registration registration = coordinator.register(
                "message", Arrays.asList(13), "satellite"
        );

        coordinator.cancel("message");

        assertNull(coordinator.arm("message", registration.generation));
        assertNull(coordinator.onTimeout("message", registration.generation));
    }

    @Test
    public void terminalFailureWhileQueueingFallsBackOnlyWhenArmed() {
        RouteFallbackCoordinator<String> coordinator = new RouteFallbackCoordinator<>();
        RouteFallbackCoordinator.Registration registration = coordinator.register(
                "message", Arrays.asList(14), "satellite"
        );

        assertNull(coordinator.onRadioStatus("message", "Canceled"));
        assertNull(coordinator.onTimeout("message", registration.generation));
        assertNotNull(coordinator.arm("message", registration.generation));
        assertFalse(coordinator.isPending("message"));
    }
}
