package com.somewearlabs.gateway;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Race-safe state machine for one controlled Radio-to-Satellite fallback.
 *
 * <p>The coordinator owns decisions only. GatewayV2 performs the actual Somewear
 * cancellation and send calls after a decision has atomically won.</p>
 */
final class RouteFallbackCoordinator<T> {
    private final Map<String, Plan<T>> plans = new LinkedHashMap<>();
    private long nextGeneration = 1L;

    synchronized Registration register(
            String messageId,
            List<Integer> radioParcelIds,
            T satelliteAttempt
    ) {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(radioParcelIds, "radioParcelIds");
        Objects.requireNonNull(satelliteAttempt, "satelliteAttempt");
        if (messageId.trim().isEmpty()) {
            throw new IllegalArgumentException("messageId must not be blank");
        }
        if (radioParcelIds.isEmpty()) {
            throw new IllegalArgumentException("radioParcelIds must not be empty");
        }
        long generation = nextGeneration++;
        if (nextGeneration <= 0L) nextGeneration = 1L;
        plans.put(
                messageId,
                new Plan<>(generation, new ArrayList<>(radioParcelIds), satelliteAttempt)
        );
        return new Registration(generation);
    }

    /**
     * Records terminal state while the gateway is still queueing Radio parcels.
     * Fallback is returned only after arm() has made the plan eligible.
     */
    synchronized Decision<T> onRadioStatus(String messageId, String rawStatus) {
        Plan<T> plan = plans.get(messageId);
        if (plan == null) return null;
        String status = normalizeStatus(rawStatus);
        if ("DELIVERED".equals(status)) {
            plans.remove(messageId);
            return null;
        }
        if (!"ERROR".equals(status)
                && !"CANCELED".equals(status)
                && !"COLLAPSED".equals(status)) return null;
        if (!plan.armed) {
            plan.failedWhileQueueing = true;
            return null;
        }
        plans.remove(messageId);
        return decision(messageId, plan);
    }

    /** Arms the current registration after all synchronous Radio send calls return. */
    synchronized Decision<T> arm(String messageId, long generation) {
        Plan<T> plan = plans.get(messageId);
        if (plan == null || plan.generation != generation) return null;
        plan.armed = true;
        if (!plan.failedWhileQueueing) return null;
        plans.remove(messageId);
        return decision(messageId, plan);
    }

    /** Only the timer belonging to the current registration may claim fallback. */
    synchronized Decision<T> onTimeout(String messageId, long generation) {
        Plan<T> plan = plans.get(messageId);
        if (plan == null || plan.generation != generation || !plan.armed) return null;
        plans.remove(messageId);
        return decision(messageId, plan);
    }

    synchronized void cancel(String messageId) {
        plans.remove(messageId);
    }

    synchronized boolean isPending(String messageId) {
        return plans.containsKey(messageId);
    }

    synchronized boolean isPending(String messageId, long generation) {
        Plan<T> plan = plans.get(messageId);
        return plan != null && plan.generation == generation;
    }

    synchronized void clear() {
        plans.clear();
    }

    private static String normalizeStatus(String rawStatus) {
        return rawStatus == null
                ? ""
                : rawStatus.replace("_", "").toUpperCase(java.util.Locale.US);
    }

    private static <T> Decision<T> decision(String messageId, Plan<T> plan) {
        return new Decision<>(messageId, plan.radioParcelIds, plan.satelliteAttempt);
    }

    private static final class Plan<T> {
        final long generation;
        final List<Integer> radioParcelIds;
        final T satelliteAttempt;
        boolean armed;
        boolean failedWhileQueueing;

        Plan(long generation, List<Integer> radioParcelIds, T satelliteAttempt) {
            this.generation = generation;
            this.radioParcelIds = radioParcelIds;
            this.satelliteAttempt = satelliteAttempt;
        }
    }

    static final class Registration {
        final long generation;

        Registration(long generation) {
            this.generation = generation;
        }
    }

    static final class Decision<T> {
        final String messageId;
        final List<Integer> radioParcelIds;
        final T satelliteAttempt;

        Decision(String messageId, List<Integer> radioParcelIds, T satelliteAttempt) {
            this.messageId = messageId;
            this.radioParcelIds = new ArrayList<>(radioParcelIds);
            this.satelliteAttempt = satelliteAttempt;
        }
    }
}
