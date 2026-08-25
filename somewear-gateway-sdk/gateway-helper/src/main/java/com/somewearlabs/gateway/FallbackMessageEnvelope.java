package com.somewearlabs.gateway;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/** Preserves the SC3 message ID across separate Radio and Satellite payloads. */
final class FallbackMessageEnvelope {
    static final String PREFIX = "\u001eSC3F1|";

    private FallbackMessageEnvelope() {}

    static String encode(String messageId, String content) {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(content, "content");
        byte[] id = messageId.getBytes(StandardCharsets.UTF_8);
        if (id.length == 0 || id.length > RadioMessageFraming.MAX_MESSAGE_ID_BYTES) {
            throw new IllegalArgumentException("message_id is too large for fallback framing");
        }
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(id) + "|" + content;
    }

    static Decoded parse(String value) {
        if (value == null || !value.startsWith(PREFIX)) return null;
        int separator = value.indexOf('|', PREFIX.length());
        if (separator < 0) throw new IllegalArgumentException("Malformed SC3 fallback envelope");
        String encodedId = value.substring(PREFIX.length(), separator);
        final byte[] idBytes;
        try {
            idBytes = Base64.getUrlDecoder().decode(encodedId);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid SC3 fallback message_id", exception);
        }
        if (idBytes.length == 0 || idBytes.length > RadioMessageFraming.MAX_MESSAGE_ID_BYTES) {
            throw new IllegalArgumentException("Invalid SC3 fallback message_id size");
        }
        String messageId = decodeUtf8(idBytes);
        if (messageId.trim().isEmpty()) {
            throw new IllegalArgumentException("SC3 fallback message_id is blank");
        }
        return new Decoded(messageId, value.substring(separator + 1));
    }

    private static String decodeUtf8(byte[] value) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(value))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Invalid UTF-8 in SC3 fallback message_id", exception);
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
