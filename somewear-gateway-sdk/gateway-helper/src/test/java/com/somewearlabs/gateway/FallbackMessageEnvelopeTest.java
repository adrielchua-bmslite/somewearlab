package com.somewearlabs.gateway;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class FallbackMessageEnvelopeTest {
    @Test
    public void preservesMessageIdAndJsonExactly() {
        String content = "{\"type\":\"位置\",\"line\":\"a|b\\n\"}";

        FallbackMessageEnvelope.Decoded decoded = FallbackMessageEnvelope.parse(
                FallbackMessageEnvelope.encode("message-ä", content)
        );

        assertEquals("message-ä", decoded.messageId);
        assertEquals(content, decoded.content);
    }

    @Test
    public void ignoresOrdinaryMessages() {
        assertNull(FallbackMessageEnvelope.parse("ordinary message"));
    }

    @Test
    public void rejectsMalformedEnvelope() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FallbackMessageEnvelope.parse(FallbackMessageEnvelope.PREFIX + "bad")
        );
    }
}
