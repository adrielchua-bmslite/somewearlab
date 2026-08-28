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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * App-private durable journal for outbound Radio frames and incomplete inbound transfers.
 *
 * <p>No workspace key or credential is stored. Encoded message fragments can contain SC3
 * application content, so the journal remains inside the gateway application's private files
 * directory and is removed after acknowledgement or expiry.</p>
 */
final class FragmentRecoveryStore {
    static final long RECORD_TTL_MS = 24L * 60L * 60L * 1_000L;
    static final int MAX_OUTGOING_RECORDS = 128;
    static final int MAX_INCOMING_RECORDS = 128;

    private static final int OUTGOING_MAGIC = 0x5343334f; // SC3O
    private static final int INCOMING_MAGIC = 0x53433349; // SC3I
    private static final int VERSION = 1;

    private final File outgoingDirectory;
    private final File incomingDirectory;

    FragmentRecoveryStore(File root) throws IOException {
        if (root == null) throw new IllegalArgumentException("Fragment recovery root is missing");
        outgoingDirectory = new File(root, "outgoing");
        incomingDirectory = new File(root, "incoming");
        requireDirectory(outgoingDirectory);
        requireDirectory(incomingDirectory);
    }

    synchronized OutgoingRecord saveOutgoing(
            String transferId,
            String messageId,
            long workspaceId,
            List<String> frames,
            long createdAt
    ) throws IOException {
        if (messageId == null || messageId.trim().isEmpty()) {
            throw new IllegalArgumentException("messageId must not be blank");
        }
        if (workspaceId <= 0L) throw new IllegalArgumentException("workspaceId must be positive");
        if (frames == null || frames.size() < 2 || frames.size() > RadioMessageFraming.MAX_FRAGMENTS) {
            throw new IllegalArgumentException("A recoverable transfer needs multiple valid frames");
        }
        String[] ordered = new String[frames.size()];
        Long checksum = null;
        for (String encoded : frames) {
            RadioMessageFraming.Frame frame = RadioMessageFraming.parse(encoded);
            if (frame == null
                    || !transferId.equals(frame.transferId)
                    || frame.count != frames.size()
                    || ordered[frame.index] != null) {
                throw new IllegalArgumentException("Outgoing SC3 fragment set is inconsistent");
            }
            if (checksum == null) checksum = frame.checksum;
            if (checksum.longValue() != frame.checksum) {
                throw new IllegalArgumentException("Outgoing SC3 fragment checksums disagree");
            }
            ordered[frame.index] = encoded;
        }
        if (Arrays.stream(ordered).anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("Outgoing SC3 fragment set is incomplete");
        }
        OutgoingRecord record = new OutgoingRecord(
                transferId,
                messageId,
                workspaceId,
                checksum == null ? 0L : checksum,
                createdAt,
                Arrays.asList(ordered)
        );
        writeOutgoing(record);
        prune(createdAt);
        trim(outgoingDirectory, MAX_OUTGOING_RECORDS);
        return record;
    }

    synchronized OutgoingRecord findOutgoing(
            String transferId,
            long checksum,
            long workspaceId,
            long now
    ) {
        pruneQuietly(now);
        for (File file : dataFiles(outgoingDirectory)) {
            OutgoingRecord record = readOutgoingQuietly(file);
            if (record != null
                    && transferId.equals(record.transferId)
                    && checksum == record.checksum
                    && workspaceId == record.workspaceId) {
                return record;
            }
        }
        return null;
    }

    synchronized OutgoingRecord findOutgoingByMessage(String messageId, long now) {
        pruneQuietly(now);
        for (File file : dataFiles(outgoingDirectory)) {
            OutgoingRecord record = readOutgoingQuietly(file);
            if (record != null && messageId.equals(record.messageId)) return record;
        }
        return null;
    }

    synchronized boolean acknowledge(
            String transferId,
            long checksum,
            long workspaceId,
            long now
    ) {
        OutgoingRecord record = findOutgoing(transferId, checksum, workspaceId, now);
        return record != null && outgoingFile(record.transferId).delete();
    }

    synchronized IncomingRecord recordIncoming(
            String encodedFrame,
            long workspaceId,
            String senderId,
            String channel,
            long receivedAt
    ) throws IOException {
        RadioMessageFraming.Frame frame = RadioMessageFraming.parse(encodedFrame);
        if (frame == null) throw new IllegalArgumentException("Content is not an SC3 fragment");
        if (workspaceId <= 0L) throw new IllegalArgumentException("workspaceId must be positive");
        String normalizedSender = senderId == null ? "" : senderId;
        String normalizedChannel = channel == null ? "UNKNOWN" : channel.toUpperCase(Locale.US);
        String key = incomingKey(workspaceId, normalizedSender, normalizedChannel, frame.transferId);
        File target = incomingFile(key);
        IncomingRecord record = target.isFile() ? readIncomingQuietly(target) : null;
        if (record == null) {
            record = new IncomingRecord(
                    key,
                    frame.transferId,
                    workspaceId,
                    normalizedSender,
                    normalizedChannel,
                    frame.checksum,
                    frame.count,
                    receivedAt,
                    receivedAt,
                    0,
                    0L,
                    new String[frame.count]
            );
        } else if (record.checksum != frame.checksum || record.fragmentCount != frame.count) {
            target.delete();
            throw new IllegalArgumentException("Conflicting persisted SC3 fragment metadata");
        }
        String previous = record.frames[frame.index];
        if (previous != null && !previous.equals(encodedFrame)) {
            target.delete();
            throw new IllegalArgumentException("Conflicting persisted SC3 fragment data");
        }
        record.frames[frame.index] = encodedFrame;
        record.lastReceivedAt = Math.max(record.lastReceivedAt, receivedAt);
        writeIncoming(record);
        prune(receivedAt);
        trim(incomingDirectory, MAX_INCOMING_RECORDS);
        return record.copy();
    }

    synchronized IncomingRecord findIncoming(String transferId, long now) {
        pruneQuietly(now);
        IncomingRecord latest = null;
        for (File file : dataFiles(incomingDirectory)) {
            IncomingRecord record = readIncomingQuietly(file);
            if (record != null && transferId.equals(record.transferId)) {
                if (latest == null || record.lastReceivedAt > latest.lastReceivedAt) latest = record;
            }
        }
        return latest == null ? null : latest.copy();
    }

    synchronized IncomingRecord findIncoming(
            String transferId,
            long workspaceId,
            String senderId,
            String channel,
            long now
    ) {
        pruneQuietly(now);
        String key = incomingKey(
                workspaceId,
                senderId == null ? "" : senderId,
                channel == null ? "UNKNOWN" : channel.toUpperCase(Locale.US),
                transferId
        );
        IncomingRecord record = readIncomingQuietly(incomingFile(key));
        return record == null || !transferId.equals(record.transferId)
                ? null
                : record.copy();
    }

    synchronized IncomingRecord findIncomingByKey(String key, long now) {
        pruneQuietly(now);
        IncomingRecord record = readIncomingQuietly(incomingFile(key));
        return record == null ? null : record.copy();
    }

    synchronized List<IncomingRecord> listIncoming(long now) {
        pruneQuietly(now);
        List<IncomingRecord> records = new ArrayList<>();
        for (File file : dataFiles(incomingDirectory)) {
            IncomingRecord record = readIncomingQuietly(file);
            if (record != null && !record.isComplete()) records.add(record.copy());
        }
        records.sort(Comparator.comparingLong(record -> record.firstReceivedAt));
        return records;
    }

    synchronized IncomingRecord markRecoveryRequested(String key, long requestedAt) throws IOException {
        File file = incomingFile(key);
        IncomingRecord record = readIncomingQuietly(file);
        if (record == null) return null;
        record.recoveryRequestCount++;
        record.lastRecoveryRequestAt = requestedAt;
        writeIncoming(record);
        return record.copy();
    }

    synchronized void removeIncoming(String key) {
        incomingFile(key).delete();
    }

    synchronized void prune(long now) throws IOException {
        pruneDirectory(outgoingDirectory, now, true);
        pruneDirectory(incomingDirectory, now, false);
    }

    private void pruneQuietly(long now) {
        try {
            prune(now);
        } catch (IOException ignored) {
            // A read can still proceed; the next successful mutation retries pruning.
        }
    }

    private void pruneDirectory(File directory, long now, boolean outgoing) throws IOException {
        for (File file : dataFiles(directory)) {
            long timestamp;
            if (outgoing) {
                OutgoingRecord record = readOutgoingQuietly(file);
                if (record == null) continue;
                timestamp = record.createdAt;
            } else {
                IncomingRecord record = readIncomingQuietly(file);
                if (record == null) continue;
                timestamp = record.lastReceivedAt;
            }
            if (timestamp <= 0L || now - timestamp > RECORD_TTL_MS) Files.deleteIfExists(file.toPath());
        }
    }

    private void trim(File directory, int maximum) throws IOException {
        List<File> files = dataFiles(directory);
        if (files.size() <= maximum) return;
        files.sort(Comparator.comparingLong(File::lastModified));
        for (int index = 0; index < files.size() - maximum; index++) {
            Files.deleteIfExists(files.get(index).toPath());
        }
    }

    private void writeOutgoing(OutgoingRecord record) throws IOException {
        File target = outgoingFile(record.transferId);
        writeAtomically(target, output -> {
            output.writeInt(OUTGOING_MAGIC);
            output.writeInt(VERSION);
            output.writeUTF(record.transferId);
            output.writeUTF(record.messageId);
            output.writeLong(record.workspaceId);
            output.writeLong(record.checksum);
            output.writeLong(record.createdAt);
            output.writeInt(record.frames.size());
            for (String frame : record.frames) output.writeUTF(frame);
        });
    }

    private OutgoingRecord readOutgoingQuietly(File file) {
        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(new FileInputStream(file)))) {
            if (input.readInt() != OUTGOING_MAGIC || input.readInt() != VERSION) {
                throw new IOException("Unsupported outgoing recovery record");
            }
            String transferId = input.readUTF();
            String messageId = input.readUTF();
            long workspaceId = input.readLong();
            long checksum = input.readLong();
            long createdAt = input.readLong();
            int count = checkedCount(input.readInt());
            List<String> frames = new ArrayList<>(count);
            for (int index = 0; index < count; index++) frames.add(input.readUTF());
            return new OutgoingRecord(
                    transferId,
                    messageId,
                    workspaceId,
                    checksum,
                    createdAt,
                    frames
            );
        } catch (IOException | RuntimeException failure) {
            file.delete();
            return null;
        }
    }

    private void writeIncoming(IncomingRecord record) throws IOException {
        File target = incomingFile(record.key);
        writeAtomically(target, output -> {
            output.writeInt(INCOMING_MAGIC);
            output.writeInt(VERSION);
            output.writeUTF(record.key);
            output.writeUTF(record.transferId);
            output.writeLong(record.workspaceId);
            output.writeUTF(record.senderId);
            output.writeUTF(record.channel);
            output.writeLong(record.checksum);
            output.writeInt(record.fragmentCount);
            output.writeLong(record.firstReceivedAt);
            output.writeLong(record.lastReceivedAt);
            output.writeInt(record.recoveryRequestCount);
            output.writeLong(record.lastRecoveryRequestAt);
            for (String frame : record.frames) {
                output.writeBoolean(frame != null);
                if (frame != null) output.writeUTF(frame);
            }
        });
    }

    private IncomingRecord readIncomingQuietly(File file) {
        if (!file.isFile()) return null;
        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(new FileInputStream(file)))) {
            if (input.readInt() != INCOMING_MAGIC || input.readInt() != VERSION) {
                throw new IOException("Unsupported incoming recovery record");
            }
            String key = input.readUTF();
            String transferId = input.readUTF();
            long workspaceId = input.readLong();
            String senderId = input.readUTF();
            String channel = input.readUTF();
            long checksum = input.readLong();
            int count = checkedCount(input.readInt());
            long firstReceivedAt = input.readLong();
            long lastReceivedAt = input.readLong();
            int requestCount = input.readInt();
            long lastRequestAt = input.readLong();
            String[] frames = new String[count];
            for (int index = 0; index < count; index++) {
                if (input.readBoolean()) frames[index] = input.readUTF();
            }
            return new IncomingRecord(
                    key,
                    transferId,
                    workspaceId,
                    senderId,
                    channel,
                    checksum,
                    count,
                    firstReceivedAt,
                    lastReceivedAt,
                    requestCount,
                    lastRequestAt,
                    frames
            );
        } catch (EOFException failure) {
            file.delete();
            return null;
        } catch (IOException | RuntimeException failure) {
            file.delete();
            return null;
        }
    }

    private static int checkedCount(int count) throws IOException {
        if (count < 1 || count > RadioMessageFraming.MAX_FRAGMENTS) {
            throw new IOException("Invalid fragment count in recovery record");
        }
        return count;
    }

    private File outgoingFile(String transferId) {
        return new File(outgoingDirectory, "out-" + digest(transferId) + ".bin");
    }

    private File incomingFile(String key) {
        return new File(incomingDirectory, "in-" + digest(key) + ".bin");
    }

    private static String incomingKey(
            long workspaceId,
            String senderId,
            String channel,
            String transferId
    ) {
        return workspaceId + "\u0000" + senderId + "\u0000" + channel + "\u0000" + transferId;
    }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : bytes) result.append(String.format(Locale.US, "%02x", item & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static List<File> dataFiles(File directory) {
        File[] files = directory.listFiles(file -> file.isFile() && file.getName().endsWith(".bin"));
        return files == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(files));
    }

    private static void requireDirectory(File directory) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Could not create fragment recovery directory");
        }
    }

    private static void writeAtomically(File target, Writer writer) throws IOException {
        requireDirectory(target.getParentFile());
        File temporary = new File(
                target.getParentFile(),
                target.getName() + "." + UUID.randomUUID() + ".part"
        );
        try {
            try (FileOutputStream file = new FileOutputStream(temporary);
                    DataOutputStream output = new DataOutputStream(
                            new BufferedOutputStream(file))) {
                writer.write(output);
                output.flush();
                file.getFD().sync();
            }
            try {
                Files.move(
                        temporary.toPath(),
                        target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(
                        temporary.toPath(),
                        target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        } finally {
            temporary.delete();
        }
    }

    private interface Writer {
        void write(DataOutputStream output) throws IOException;
    }

    static final class OutgoingRecord {
        final String transferId;
        final String messageId;
        final long workspaceId;
        final long checksum;
        final long createdAt;
        final List<String> frames;

        OutgoingRecord(
                String transferId,
                String messageId,
                long workspaceId,
                long checksum,
                long createdAt,
                List<String> frames
        ) {
            this.transferId = transferId;
            this.messageId = messageId;
            this.workspaceId = workspaceId;
            this.checksum = checksum;
            this.createdAt = createdAt;
            this.frames = new ArrayList<>(frames);
        }
    }

    static final class IncomingRecord {
        final String key;
        final String transferId;
        final long workspaceId;
        final String senderId;
        final String channel;
        final long checksum;
        final int fragmentCount;
        final long firstReceivedAt;
        long lastReceivedAt;
        int recoveryRequestCount;
        long lastRecoveryRequestAt;
        final String[] frames;

        IncomingRecord(
                String key,
                String transferId,
                long workspaceId,
                String senderId,
                String channel,
                long checksum,
                int fragmentCount,
                long firstReceivedAt,
                long lastReceivedAt,
                int recoveryRequestCount,
                long lastRecoveryRequestAt,
                String[] frames
        ) {
            this.key = key;
            this.transferId = transferId;
            this.workspaceId = workspaceId;
            this.senderId = senderId;
            this.channel = channel;
            this.checksum = checksum;
            this.fragmentCount = fragmentCount;
            this.firstReceivedAt = firstReceivedAt;
            this.lastReceivedAt = lastReceivedAt;
            this.recoveryRequestCount = recoveryRequestCount;
            this.lastRecoveryRequestAt = lastRecoveryRequestAt;
            this.frames = frames;
        }

        int receivedCount() {
            int count = 0;
            for (String frame : frames) if (frame != null) count++;
            return count;
        }

        boolean isComplete() {
            return receivedCount() == fragmentCount;
        }

        List<Integer> missingIndexes() {
            List<Integer> missing = new ArrayList<>();
            for (int index = 0; index < frames.length; index++) {
                if (frames[index] == null) missing.add(index);
            }
            return missing;
        }

        List<String> receivedFrames() {
            List<String> result = new ArrayList<>();
            for (String frame : frames) if (frame != null) result.add(frame);
            return result;
        }

        IncomingRecord copy() {
            return new IncomingRecord(
                    key,
                    transferId,
                    workspaceId,
                    senderId,
                    channel,
                    checksum,
                    fragmentCount,
                    firstReceivedAt,
                    lastReceivedAt,
                    recoveryRequestCount,
                    lastRecoveryRequestAt,
                    frames.clone()
            );
        }
    }
}
