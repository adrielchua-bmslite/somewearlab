package com.somewearlabs.gateway;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Compact control messages for receiver-confirmed SC3 Radio fragment recovery. */
final class FragmentRecoveryProtocol {
    static final String REQUEST_PREFIX = "\u001eSC3Q1|";
    static final String ACK_PREFIX = "\u001eSC3A1|";
    static final int MAX_INDEXES_PER_REQUEST = 24;

    private FragmentRecoveryProtocol() {}

    static String encodeRequest(String transferId, long checksum, List<Integer> indexes) {
        validateTransferId(transferId);
        validateChecksum(checksum);
        List<Integer> normalized = normalizeIndexes(indexes);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("A recovery request needs at least one fragment index");
        }
        if (normalized.size() > MAX_INDEXES_PER_REQUEST) {
            throw new IllegalArgumentException("A recovery request contains too many fragment indexes");
        }
        StringBuilder result = new StringBuilder(REQUEST_PREFIX)
                .append(transferId)
                .append('|')
                .append(Long.toString(checksum, 36))
                .append('|');
        for (int index = 0; index < normalized.size(); index++) {
            if (index > 0) result.append(',');
            result.append(Integer.toString(normalized.get(index), 36));
        }
        return result.toString();
    }

    static String encodeAck(String transferId, long checksum) {
        validateTransferId(transferId);
        validateChecksum(checksum);
        return ACK_PREFIX + transferId + '|' + Long.toString(checksum, 36);
    }

    static Control parse(String content) {
        if (content == null) return null;
        if (content.startsWith(REQUEST_PREFIX)) {
            String[] fields = content.substring(REQUEST_PREFIX.length()).split("\\|", -1);
            if (fields.length != 3) {
                throw new IllegalArgumentException("Malformed SC3 fragment recovery request");
            }
            validateTransferId(fields[0]);
            long checksum = parseChecksum(fields[1]);
            if (fields[2].isEmpty()) {
                throw new IllegalArgumentException("SC3 fragment recovery request has no indexes");
            }
            String[] rawIndexes = fields[2].split(",", -1);
            if (rawIndexes.length > MAX_INDEXES_PER_REQUEST) {
                throw new IllegalArgumentException("SC3 fragment recovery request has too many indexes");
            }
            List<Integer> indexes = new ArrayList<>(rawIndexes.length);
            for (String raw : rawIndexes) {
                try {
                    int index = Integer.parseInt(raw, 36);
                    if (index < 0 || index >= RadioMessageFraming.MAX_FRAGMENTS) {
                        throw new IllegalArgumentException("Invalid SC3 recovery fragment index");
                    }
                    indexes.add(index);
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("Invalid SC3 recovery fragment index", exception);
                }
            }
            return Control.request(fields[0], checksum, normalizeIndexes(indexes));
        }
        if (content.startsWith(ACK_PREFIX)) {
            String[] fields = content.substring(ACK_PREFIX.length()).split("\\|", -1);
            if (fields.length != 2) {
                throw new IllegalArgumentException("Malformed SC3 fragment completion acknowledgement");
            }
            validateTransferId(fields[0]);
            return Control.ack(fields[0], parseChecksum(fields[1]));
        }
        return null;
    }

    static List<List<Integer>> batches(List<Integer> indexes) {
        List<Integer> normalized = normalizeIndexes(indexes);
        List<List<Integer>> result = new ArrayList<>();
        for (int start = 0; start < normalized.size(); start += MAX_INDEXES_PER_REQUEST) {
            int end = Math.min(normalized.size(), start + MAX_INDEXES_PER_REQUEST);
            result.add(Collections.unmodifiableList(new ArrayList<>(normalized.subList(start, end))));
        }
        return Collections.unmodifiableList(result);
    }

    private static List<Integer> normalizeIndexes(List<Integer> indexes) {
        Objects.requireNonNull(indexes, "indexes");
        Set<Integer> unique = new LinkedHashSet<>();
        for (Integer index : indexes) {
            if (index == null || index < 0 || index >= RadioMessageFraming.MAX_FRAGMENTS) {
                throw new IllegalArgumentException("Invalid SC3 recovery fragment index");
            }
            unique.add(index);
        }
        List<Integer> result = new ArrayList<>(unique);
        Collections.sort(result);
        return result;
    }

    private static void validateTransferId(String transferId) {
        if (transferId == null
                || transferId.length() < 8
                || transferId.length() > 64
                || !transferId.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid SC3 recovery transfer ID");
        }
    }

    private static void validateChecksum(long checksum) {
        if (checksum < 0L || checksum > 0xffff_ffffL) {
            throw new IllegalArgumentException("Invalid SC3 recovery checksum");
        }
    }

    private static long parseChecksum(String value) {
        try {
            long checksum = Long.parseLong(value, 36);
            validateChecksum(checksum);
            return checksum;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid SC3 recovery checksum", exception);
        }
    }

    static final class Control {
        enum Kind { REQUEST, ACK }

        final Kind kind;
        final String transferId;
        final long checksum;
        final List<Integer> indexes;

        private Control(Kind kind, String transferId, long checksum, List<Integer> indexes) {
            this.kind = kind;
            this.transferId = transferId;
            this.checksum = checksum;
            this.indexes = indexes;
        }

        static Control request(String transferId, long checksum, List<Integer> indexes) {
            return new Control(
                    Kind.REQUEST,
                    transferId,
                    checksum,
                    Collections.unmodifiableList(new ArrayList<>(indexes))
            );
        }

        static Control ack(String transferId, long checksum) {
            return new Control(Kind.ACK, transferId, checksum, Collections.emptyList());
        }
    }
}
