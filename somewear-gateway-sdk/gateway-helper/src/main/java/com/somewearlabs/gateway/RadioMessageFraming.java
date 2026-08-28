package com.somewearlabs.gateway;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.CRC32;

/**
 * SC3-owned fragmentation used before payloads enter the retained Somewear core.
 *
 * <p>Every encoded frame is sent as an ordinary Somewear MessagePayload. This is
 * deliberately separate from Somewear's PackageType.Part mechanism because the
 * retained core does not preserve the caller's route reliably for composite
 * children. The historical SC3R1 prefix is retained for wire compatibility, but
 * these frames remain readable on both Radio and Satellite for v14 handover
 * compatibility. New Satellite sends use the retained core's native composite
 * transport instead.</p>
 */
final class RadioMessageFraming {
    static final String PREFIX = "\u001eSC3R1|";
    static final int DEFAULT_CHUNK_BYTES = 128;
    static final int MIN_CHUNK_BYTES = 32;
    static final int MAX_WIRE_BYTES = 64 * 1024;
    static final int MAX_MESSAGE_ID_BYTES = 4 * 1024;
    static final int MAX_FRAGMENTS = 2_048;

    private RadioMessageFraming() {}

    static List<String> split(
            String messageId,
            String content,
            String transferId,
            int chunkBytes
    ) {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(content, "content");
        validateTransferId(transferId);
        if (chunkBytes < MIN_CHUNK_BYTES || chunkBytes > DEFAULT_CHUNK_BYTES) {
            throw new IllegalArgumentException("Unsupported transport fragment size");
        }

        byte[] messageIdBytes = messageId.getBytes(StandardCharsets.UTF_8);
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        if (messageIdBytes.length == 0 || messageIdBytes.length > MAX_MESSAGE_ID_BYTES) {
            throw new IllegalArgumentException("message_id is too large for transport framing");
        }
        int wireLength = 2 + messageIdBytes.length + contentBytes.length;
        if (wireLength > MAX_WIRE_BYTES) {
            throw new IllegalArgumentException("Message is too large for transport framing");
        }

        byte[] wire = new byte[wireLength];
        wire[0] = (byte) ((messageIdBytes.length >>> 8) & 0xff);
        wire[1] = (byte) (messageIdBytes.length & 0xff);
        System.arraycopy(messageIdBytes, 0, wire, 2, messageIdBytes.length);
        System.arraycopy(contentBytes, 0, wire, 2 + messageIdBytes.length, contentBytes.length);

        int count = (wire.length + chunkBytes - 1) / chunkBytes;
        if (count < 1 || count > MAX_FRAGMENTS) {
            throw new IllegalArgumentException("Message requires too many transport fragments");
        }
        String countText = Integer.toString(count, 36);
        String checksum = Long.toString(checksum(wire), 36);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        List<String> frames = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int start = index * chunkBytes;
            int end = Math.min(start + chunkBytes, wire.length);
            byte[] chunk = Arrays.copyOfRange(wire, start, end);
            frames.add(
                    PREFIX
                            + transferId + "|"
                            + Integer.toString(index, 36) + "|"
                            + countText + "|"
                            + checksum + "|"
                            + encoder.encodeToString(chunk)
            );
        }
        return frames;
    }

    static Frame parse(String content) {
        if (content == null || !content.startsWith(PREFIX)) return null;
        String[] fields = content.substring(PREFIX.length()).split("\\|", -1);
        if (fields.length != 5) throw new IllegalArgumentException("Malformed SC3 radio frame");
        validateTransferId(fields[0]);
        int index = parseBase36Int(fields[1], "fragment index");
        int count = parseBase36Int(fields[2], "fragment count");
        long checksum = parseBase36Long(fields[3], "fragment checksum");
        if (count < 1 || count > MAX_FRAGMENTS || index < 0 || index >= count) {
            throw new IllegalArgumentException("Invalid SC3 radio fragment position");
        }
        if (checksum < 0L || checksum > 0xffff_ffffL) {
            throw new IllegalArgumentException("Invalid SC3 radio fragment checksum");
        }
        final byte[] data;
        try {
            data = Base64.getUrlDecoder().decode(fields[4]);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid SC3 radio fragment data", exception);
        }
        if (data.length < 1 || data.length > DEFAULT_CHUNK_BYTES) {
            throw new IllegalArgumentException("Invalid SC3 radio fragment size");
        }
        return new Frame(fields[0], index, count, checksum, data);
    }

    static Decoded decode(byte[] wire) {
        if (wire.length < 3 || wire.length > MAX_WIRE_BYTES) {
            throw new IllegalArgumentException("Invalid reassembled SC3 radio message size");
        }
        int messageIdLength = ((wire[0] & 0xff) << 8) | (wire[1] & 0xff);
        if (messageIdLength < 1
                || messageIdLength > MAX_MESSAGE_ID_BYTES
                || 2 + messageIdLength > wire.length) {
            throw new IllegalArgumentException("Invalid reassembled SC3 message_id");
        }
        String messageId = decodeUtf8(wire, 2, messageIdLength);
        String content = decodeUtf8(wire, 2 + messageIdLength, wire.length - 2 - messageIdLength);
        if (messageId.trim().isEmpty()) {
            throw new IllegalArgumentException("Reassembled SC3 message_id is blank");
        }
        return new Decoded(messageId, content);
    }

    static long checksum(byte[] bytes) {
        CRC32 crc32 = new CRC32();
        crc32.update(bytes);
        return crc32.getValue();
    }

    private static String decodeUtf8(byte[] bytes, int offset, int length) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, length))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Invalid UTF-8 in reassembled SC3 message", exception);
        }
    }

    private static void validateTransferId(String transferId) {
        if (transferId == null
                || transferId.length() < 8
                || transferId.length() > 64
                || !transferId.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid SC3 radio transfer ID");
        }
    }

    private static int parseBase36Int(String value, String label) {
        try {
            return Integer.parseInt(value, 36);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid " + label, exception);
        }
    }

    private static long parseBase36Long(String value, String label) {
        try {
            return Long.parseLong(value, 36);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid " + label, exception);
        }
    }

    static final class Frame {
        final String transferId;
        final int index;
        final int count;
        final long checksum;
        final byte[] data;

        Frame(String transferId, int index, int count, long checksum, byte[] data) {
            this.transferId = transferId;
            this.index = index;
            this.count = count;
            this.checksum = checksum;
            this.data = data;
        }
    }

    static final class Decoded {
        final String messageId;
        final String content;

        Decoded(String messageId, String content) {
            this.messageId = messageId;
            this.content = content;
        }
    }
}

/** Bounded, duplicate-safe and out-of-order-safe receiver for SC3 transport frames. */
final class RadioMessageReassembler {
    private static final long ASSEMBLY_TTL_MS = 10 * 60_000L;
    private static final int MAX_ACTIVE_ASSEMBLIES = 64;

    private final Map<String, Assembly> assemblies = new HashMap<>();

    synchronized Result accept(
            String content,
            long workspaceId,
            String senderId,
            String channel,
            long receivedAt
    ) {
        final RadioMessageFraming.Frame frame;
        try {
            frame = RadioMessageFraming.parse(content);
        } catch (IllegalArgumentException exception) {
            return Result.invalid(exception.getMessage());
        }
        if (frame == null) return Result.notFrame();

        long localNow = System.currentTimeMillis();
        expire(localNow);
        String sender = senderId == null ? "" : senderId;
        String key = workspaceId + "\u0000" + sender + "\u0000" + channel + "\u0000" + frame.transferId;
        Assembly assembly = assemblies.get(key);
        if (assembly == null) {
            if (assemblies.size() >= MAX_ACTIVE_ASSEMBLIES) evictOldest();
            assembly = new Assembly(frame.count, frame.checksum, localNow, receivedAt);
            assemblies.put(key, assembly);
        } else if (assembly.count != frame.count || assembly.checksum != frame.checksum) {
            assemblies.remove(key);
            return Result.invalid("Conflicting SC3 radio fragment metadata");
        }

        byte[] previous = assembly.parts[frame.index];
        if (previous != null) {
            if (!Arrays.equals(previous, frame.data)) {
                assemblies.remove(key);
                return Result.invalid("Conflicting duplicate SC3 radio fragment");
            }
            return Result.pending();
        }

        assembly.parts[frame.index] = frame.data;
        assembly.receivedCount++;
        assembly.totalBytes += frame.data.length;
        assembly.lastTouchedAt = localNow;
        assembly.latestReceivedAt = Math.max(assembly.latestReceivedAt, receivedAt);
        if (assembly.totalBytes > RadioMessageFraming.MAX_WIRE_BYTES) {
            assemblies.remove(key);
            return Result.invalid("SC3 radio message exceeded the reassembly limit");
        }
        if (assembly.receivedCount != assembly.count) return Result.pending();

        assemblies.remove(key);
        ByteArrayOutputStream output = new ByteArrayOutputStream(assembly.totalBytes);
        for (byte[] part : assembly.parts) output.write(part, 0, part.length);
        byte[] wire = output.toByteArray();
        if (RadioMessageFraming.checksum(wire) != assembly.checksum) {
            return Result.invalid("SC3 radio message checksum failed");
        }
        try {
            RadioMessageFraming.Decoded decoded = RadioMessageFraming.decode(wire);
            return Result.complete(decoded.messageId, decoded.content, assembly.latestReceivedAt);
        } catch (IllegalArgumentException exception) {
            return Result.invalid(exception.getMessage());
        }
    }

    synchronized int activeCount() {
        return assemblies.size();
    }

    synchronized void clear() {
        assemblies.clear();
    }

    private void expire(long now) {
        assemblies.entrySet().removeIf(
                entry -> now - entry.getValue().lastTouchedAt > ASSEMBLY_TTL_MS
        );
    }

    private void evictOldest() {
        assemblies.entrySet().stream()
                .min(Comparator.comparingLong(entry -> entry.getValue().lastTouchedAt))
                .ifPresent(entry -> assemblies.remove(entry.getKey()));
    }

    private static final class Assembly {
        final int count;
        final long checksum;
        final byte[][] parts;
        int receivedCount;
        int totalBytes;
        long lastTouchedAt;
        long latestReceivedAt;

        Assembly(int count, long checksum, long localNow, long receivedAt) {
            this.count = count;
            this.checksum = checksum;
            this.parts = new byte[count][];
            this.lastTouchedAt = localNow;
            this.latestReceivedAt = receivedAt;
        }
    }

    static final class Result {
        enum Kind { NOT_FRAME, PENDING, COMPLETE, INVALID }

        final Kind kind;
        final String messageId;
        final String content;
        final String error;
        final long completedAt;

        private Result(
                Kind kind,
                String messageId,
                String content,
                String error,
                long completedAt
        ) {
            this.kind = kind;
            this.messageId = messageId;
            this.content = content;
            this.error = error;
            this.completedAt = completedAt;
        }

        static Result notFrame() {
            return new Result(Kind.NOT_FRAME, null, null, null, 0L);
        }

        static Result pending() {
            return new Result(Kind.PENDING, null, null, null, 0L);
        }

        static Result complete(String messageId, String content, long completedAt) {
            return new Result(Kind.COMPLETE, messageId, content, null, completedAt);
        }

        static Result invalid(String error) {
            return new Result(Kind.INVALID, null, null, error, 0L);
        }
    }
}
