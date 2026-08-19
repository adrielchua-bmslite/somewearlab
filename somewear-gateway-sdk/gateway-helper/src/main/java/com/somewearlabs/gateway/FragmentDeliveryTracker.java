package com.somewearlabs.gateway;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Aggregates multiple ordinary radio parcels into one SC3 delivery result. */
final class FragmentDeliveryTracker {
    private final Map<Integer, Part> partsByParcel = new LinkedHashMap<>();
    private final Map<String, Transfer> transfersByMessage = new LinkedHashMap<>();

    synchronized void register(String messageId, String requestedChannel, List<Integer> parcelIds) {
        if (parcelIds.isEmpty()) throw new IllegalArgumentException("parcelIds must not be empty");
        removeTransfer(messageId);
        Transfer transfer = new Transfer(messageId, requestedChannel, parcelIds.size());
        transfersByMessage.put(messageId, transfer);
        for (int index = 0; index < parcelIds.size(); index++) {
            int parcelId = parcelIds.get(index);
            if (partsByParcel.containsKey(parcelId)) {
                removeTransfer(messageId);
                throw new IllegalArgumentException("Duplicate Somewear parcel ID");
            }
            Part part = new Part(transfer, index, parcelId);
            transfer.parts.add(part);
            partsByParcel.put(parcelId, part);
        }
    }

    synchronized Update update(
            int parcelId,
            String rawStatus,
            String deliveredChannel,
            String errorReason,
            long updatedAt
    ) {
        Part part = partsByParcel.get(parcelId);
        if (part == null) return null;
        Transfer transfer = part.transfer;
        if (transfer.terminal) return null;

        part.status = normalizeStatus(rawStatus);
        if (isMeaningfulChannel(deliveredChannel)) transfer.channel = deliveredChannel.toUpperCase(Locale.US);
        if (isFailure(part.status)) {
            transfer.terminal = true;
            String detail = errorReason == null || errorReason.trim().isEmpty()
                    ? "Somewear rejected a radio fragment"
                    : errorReason;
            Update update = new Update(
                    transfer.messageId,
                    part.status,
                    transfer.channel,
                    "Fragment " + (part.index + 1) + "/" + transfer.expectedCount + ": " + detail,
                    updatedAt,
                    transfer.expectedCount
            );
            removeTransfer(transfer.messageId);
            return update;
        }

        String aggregate = aggregateStatus(transfer.parts);
        boolean delivered = "DELIVERED".equals(aggregate);
        Update update = new Update(
                transfer.messageId,
                aggregate,
                transfer.channel,
                null,
                updatedAt,
                transfer.expectedCount
        );
        if (delivered) {
            transfer.terminal = true;
            removeTransfer(transfer.messageId);
        }
        return update;
    }

    synchronized Update fail(
            String messageId,
            String channel,
            String errorReason,
            long updatedAt
    ) {
        Transfer transfer = transfersByMessage.get(messageId);
        if (transfer == null) return null;
        Update update = new Update(
                messageId,
                "ERROR",
                channel,
                errorReason,
                updatedAt,
                transfer.expectedCount
        );
        removeTransfer(messageId);
        return update;
    }

    synchronized void clear() {
        partsByParcel.clear();
        transfersByMessage.clear();
    }

    private void removeTransfer(String messageId) {
        Transfer previous = transfersByMessage.remove(messageId);
        if (previous == null) return;
        for (Part part : previous.parts) partsByParcel.remove(part.parcelId);
    }

    private static String aggregateStatus(List<Part> parts) {
        boolean allDelivered = true;
        boolean transferring = false;
        boolean pending = false;
        boolean queued = false;
        for (Part part : parts) {
            allDelivered &= "DELIVERED".equals(part.status);
            transferring |= "TRANSFERRING".equals(part.status);
            pending |= "PENDING_TRANSFER".equals(part.status);
            queued |= "QUEUED".equals(part.status);
        }
        if (allDelivered) return "DELIVERED";
        if (transferring) return "TRANSFERRING";
        if (pending) return "PENDING_TRANSFER";
        if (queued) return "QUEUED";
        return "NONE";
    }

    private static String normalizeStatus(String rawStatus) {
        String compact = rawStatus == null
                ? ""
                : rawStatus.replace("_", "").toUpperCase(Locale.US);
        switch (compact) {
            case "QUEUED":
                return "QUEUED";
            case "CONNECTING":
            case "PENDINGTRANSFER":
                return "PENDING_TRANSFER";
            case "SENDING":
            case "TRANSFERRING":
                return "TRANSFERRING";
            case "DELIVERED":
                return "DELIVERED";
            case "ERROR":
                return "ERROR";
            case "CANCELED":
                return "CANCELED";
            case "COLLAPSED":
                return "COLLAPSED";
            default:
                return "NONE";
        }
    }

    private static boolean isFailure(String status) {
        return "ERROR".equals(status)
                || "CANCELED".equals(status)
                || "COLLAPSED".equals(status);
    }

    private static boolean isMeaningfulChannel(String channel) {
        if (channel == null) return false;
        String normalized = channel.toUpperCase(Locale.US);
        return !normalized.isEmpty()
                && !"NONE".equals(normalized)
                && !"UNKNOWN".equals(normalized)
                && !"NULL".equals(normalized);
    }

    private static final class Transfer {
        final String messageId;
        final int expectedCount;
        final List<Part> parts = new ArrayList<>();
        String channel;
        boolean terminal;

        Transfer(String messageId, String channel, int expectedCount) {
            this.messageId = messageId;
            this.channel = channel.toUpperCase(Locale.US);
            this.expectedCount = expectedCount;
        }
    }

    private static final class Part {
        final Transfer transfer;
        final int index;
        final int parcelId;
        String status = "QUEUED";

        Part(Transfer transfer, int index, int parcelId) {
            this.transfer = transfer;
            this.index = index;
            this.parcelId = parcelId;
        }
    }

    static final class Update {
        final String messageId;
        final String status;
        final String channel;
        final String errorReason;
        final long updatedAt;
        final int fragmentCount;

        Update(
                String messageId,
                String status,
                String channel,
                String errorReason,
                long updatedAt,
                int fragmentCount
        ) {
            this.messageId = messageId;
            this.status = status;
            this.channel = channel;
            this.errorReason = errorReason;
            this.updatedAt = updatedAt;
            this.fragmentCount = fragmentCount;
        }
    }
}
