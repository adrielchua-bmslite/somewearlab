package com.somewearlabs.gateway;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * App-private durable inbox for completed messages handed from Somewear Core to SC3.
 *
 * Records remain available across gateway process restarts until SC3 explicitly
 * acknowledges them. This is deliberately separate from the transport-fragment
 * recovery journal: only complete application messages are stored here.
 */
final class IncomingMessageStore {
    private static final int RECORD_MAGIC = 0x53433349; // SC3I
    private static final int STATE_MAGIC = 0x53433353; // SC3S
    private static final int VERSION = 1;
    private static final int DEFAULT_MAX_RECORDS = 2_000;
    private static final int MAX_TEXT_BYTES = 8 * 1_024 * 1_024;
    private static final String STATE_FILE = "state.bin";
    private static final String RECORD_PREFIX = "message-";
    private static final String RECORD_SUFFIX = ".bin";

    private final File root;
    private final int maxRecords;
    private long lastAllocatedSequence;
    private long acknowledgedThroughSequence;
    private long droppedCount;

    IncomingMessageStore(File root) throws IOException {
        this(root, DEFAULT_MAX_RECORDS);
    }

    IncomingMessageStore(File root, int maxRecords) throws IOException {
        if (maxRecords < 1) throw new IllegalArgumentException("maxRecords must be positive");
        this.root = root;
        this.maxRecords = maxRecords;
        if (!root.exists() && !root.mkdirs()) {
            throw new IOException("Could not create incoming-message store");
        }
        if (!root.isDirectory()) throw new IOException("Incoming-message store is not a directory");
        readState();
        reconcileRecords();
        trimIfNeeded();
    }

    synchronized Record append(
            String messageId,
            String content,
            long workspaceId,
            String senderId,
            String channel,
            long receivedAt
    ) throws IOException {
        if (messageId == null || messageId.isEmpty()) {
            throw new IllegalArgumentException("messageId must not be blank");
        }
        if (content == null) throw new IllegalArgumentException("content must not be null");
        if (workspaceId <= 0L) throw new IllegalArgumentException("workspaceId must be positive");
        long wallClockFloor = safeSequenceFloor(System.currentTimeMillis());
        long sequence = Math.max(lastAllocatedSequence + 1L, wallClockFloor);
        Record record = new Record(
                sequence,
                messageId,
                content,
                workspaceId,
                senderId,
                channel,
                receivedAt
        );
        writeRecord(record);
        lastAllocatedSequence = sequence;
        try {
            writeState();
            trimIfNeeded();
        } catch (IOException ignored) {
            // The message record is already durable. A later open/append reconciles
            // lastAllocatedSequence from record files and retries state maintenance.
        }
        return record;
    }

    synchronized List<Record> listAfter(long afterSequence, int limit) {
        if (afterSequence < 0L) throw new IllegalArgumentException("afterSequence must be non-negative");
        if (limit < 1) throw new IllegalArgumentException("limit must be positive");
        List<Record> records = readRecords();
        ArrayList<Record> result = new ArrayList<>(Math.min(records.size(), limit));
        for (Record record : records) {
            if (record.sequence > acknowledgedThroughSequence
                    && record.sequence > afterSequence) {
                result.add(record);
                if (result.size() >= limit) break;
            }
        }
        return result;
    }

    synchronized Stats acknowledgeThrough(long sequence) throws IOException {
        if (sequence < 0L) throw new IllegalArgumentException("sequence must be non-negative");
        // Reconcile first in case another gateway component opened this same
        // private store during Android service/provider startup.
        readRecords();
        if (sequence > lastAllocatedSequence) {
            throw new IllegalArgumentException("Cannot acknowledge a future sequence");
        }
        if (sequence > acknowledgedThroughSequence) {
            acknowledgedThroughSequence = sequence;
            writeState();
            deleteAcknowledgedRecords();
        }
        return stats();
    }

    synchronized Stats stats() {
        List<Record> records = readRecords();
        long oldest = 0L;
        int queued = 0;
        for (Record record : records) {
            if (record.sequence <= acknowledgedThroughSequence) continue;
            if (oldest == 0L) oldest = record.sequence;
            queued++;
        }
        return new Stats(
                queued,
                oldest,
                lastAllocatedSequence,
                acknowledgedThroughSequence,
                droppedCount
        );
    }

    private void reconcileRecords() {
        for (Record record : readRecords()) {
            lastAllocatedSequence = Math.max(lastAllocatedSequence, record.sequence);
        }
        if (acknowledgedThroughSequence > lastAllocatedSequence) {
            acknowledgedThroughSequence = lastAllocatedSequence;
        }
        deleteAcknowledgedRecords();
    }

    private void trimIfNeeded() throws IOException {
        List<Record> live = new ArrayList<>();
        for (Record record : readRecords()) {
            if (record.sequence > acknowledgedThroughSequence) live.add(record);
        }
        int excess = live.size() - maxRecords;
        if (excess <= 0) return;

        long previousAck = acknowledgedThroughSequence;
        long previousDropped = droppedCount;
        acknowledgedThroughSequence = Math.max(
                acknowledgedThroughSequence,
                live.get(excess - 1).sequence
        );
        droppedCount += excess;
        try {
            writeState();
        } catch (IOException failure) {
            acknowledgedThroughSequence = previousAck;
            droppedCount = previousDropped;
            throw failure;
        }
        deleteAcknowledgedRecords();
    }

    private void deleteAcknowledgedRecords() {
        for (File file : recordFiles()) {
            long sequence = sequenceFromFile(file);
            if (sequence > 0L && sequence <= acknowledgedThroughSequence) {
                // State is persisted first, so a failed delete can only leave a
                // filtered stale file; it cannot replay an acknowledged message.
                file.delete();
            }
        }
    }

    private List<Record> readRecords() {
        ArrayList<Record> records = new ArrayList<>();
        for (File file : recordFiles()) {
            try {
                records.add(readRecord(file));
            } catch (IOException ignored) {
                // Never crash the gateway over one corrupt private record. It is
                // intentionally left on disk for post-mortem inspection.
            }
        }
        records.sort(Comparator.comparingLong(record -> record.sequence));
        if (!records.isEmpty()) {
            lastAllocatedSequence = Math.max(
                    lastAllocatedSequence,
                    records.get(records.size() - 1).sequence
            );
        }
        return records;
    }

    private File[] recordFiles() {
        File[] files = root.listFiles((directory, name) ->
                name.startsWith(RECORD_PREFIX) && name.endsWith(RECORD_SUFFIX));
        return files == null ? new File[0] : files;
    }

    private void readState() throws IOException {
        File state = new File(root, STATE_FILE);
        if (!state.exists()) return;
        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(new FileInputStream(state)))) {
            if (input.readInt() != STATE_MAGIC || input.readInt() != VERSION) {
                throw new IOException("Unsupported incoming-message state");
            }
            lastAllocatedSequence = Math.max(0L, input.readLong());
            acknowledgedThroughSequence = Math.max(0L, input.readLong());
            droppedCount = Math.max(0L, input.readLong());
        } catch (EOFException failure) {
            throw new IOException("Truncated incoming-message state", failure);
        }
    }

    private void writeState() throws IOException {
        writeAtomically(new File(root, STATE_FILE), output -> {
            output.writeInt(STATE_MAGIC);
            output.writeInt(VERSION);
            output.writeLong(lastAllocatedSequence);
            output.writeLong(acknowledgedThroughSequence);
            output.writeLong(droppedCount);
        });
    }

    private void writeRecord(Record record) throws IOException {
        writeAtomically(recordFile(record.sequence), output -> {
            output.writeInt(RECORD_MAGIC);
            output.writeInt(VERSION);
            output.writeLong(record.sequence);
            writeText(output, record.messageId, false);
            writeText(output, record.content, false);
            output.writeLong(record.workspaceId);
            writeText(output, record.senderId, true);
            writeText(output, record.channel, true);
            output.writeLong(record.receivedAt);
        });
    }

    private Record readRecord(File file) throws IOException {
        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(new FileInputStream(file)))) {
            if (input.readInt() != RECORD_MAGIC || input.readInt() != VERSION) {
                throw new IOException("Unsupported incoming-message record");
            }
            return new Record(
                    input.readLong(),
                    readText(input, false),
                    readText(input, false),
                    input.readLong(),
                    readText(input, true),
                    readText(input, true),
                    input.readLong()
            );
        } catch (EOFException failure) {
            throw new IOException("Truncated incoming-message record", failure);
        }
    }

    private void writeAtomically(File target, Writer writer) throws IOException {
        File temporary = new File(root, target.getName() + ".tmp");
        try (FileOutputStream file = new FileOutputStream(temporary);
             DataOutputStream output = new DataOutputStream(new BufferedOutputStream(file))) {
            writer.write(output);
            output.flush();
            file.getFD().sync();
        }
        try {
            Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );
        } finally {
            if (temporary.exists()) temporary.delete();
        }
    }

    private File recordFile(long sequence) {
        return new File(root, String.format("%s%019d%s", RECORD_PREFIX, sequence, RECORD_SUFFIX));
    }

    private static long sequenceFromFile(File file) {
        String name = file.getName();
        try {
            return Long.parseLong(name.substring(
                    RECORD_PREFIX.length(),
                    name.length() - RECORD_SUFFIX.length()
            ));
        } catch (RuntimeException ignored) {
            return -1L;
        }
    }

    private static long safeSequenceFloor(long nowMillis) {
        if (nowMillis <= 0L) return 1L;
        if (nowMillis > Long.MAX_VALUE / 1_000L) return nowMillis;
        return nowMillis * 1_000L;
    }

    private static void writeText(DataOutputStream output, String value, boolean nullable)
            throws IOException {
        if (value == null) {
            if (!nullable) throw new IOException("Required text is null");
            output.writeInt(-1);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_TEXT_BYTES) throw new IOException("Text exceeds private inbox limit");
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readText(DataInputStream input, boolean nullable) throws IOException {
        int length = input.readInt();
        if (length == -1 && nullable) return null;
        if (length < 0 || length > MAX_TEXT_BYTES) throw new IOException("Invalid text length");
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private interface Writer {
        void write(DataOutputStream output) throws IOException;
    }

    static final class Record {
        final long sequence;
        final String messageId;
        final String content;
        final long workspaceId;
        final String senderId;
        final String channel;
        final long receivedAt;

        Record(
                long sequence,
                String messageId,
                String content,
                long workspaceId,
                String senderId,
                String channel,
                long receivedAt
        ) {
            this.sequence = sequence;
            this.messageId = messageId;
            this.content = content;
            this.workspaceId = workspaceId;
            this.senderId = senderId;
            this.channel = channel;
            this.receivedAt = receivedAt;
        }
    }

    static final class Stats {
        final int queuedCount;
        final long oldestSequence;
        final long latestSequence;
        final long acknowledgedThroughSequence;
        final long droppedCount;

        Stats(
                int queuedCount,
                long oldestSequence,
                long latestSequence,
                long acknowledgedThroughSequence,
                long droppedCount
        ) {
            this.queuedCount = queuedCount;
            this.oldestSequence = oldestSequence;
            this.latestSequence = latestSequence;
            this.acknowledgedThroughSequence = acknowledgedThroughSequence;
            this.droppedCount = droppedCount;
        }
    }
}
