package com.nowcoder.community.im.common.support;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConversationIdSupportTest {

    @Test
    void conversationIdShouldUseStableCanonicalFormatForEitherUserOrder() {
        UUID first = UUID.fromString("00000000-0000-7000-8000-000000000001");
        UUID second = UUID.fromString("00000000-0000-7000-8000-000000000002");
        String expected = "00000000-0000-7000-8000-000000000001_00000000-0000-7000-8000-000000000002";

        assertEquals(expected, ConversationIdSupport.conversationId(first, second));
        assertEquals(expected, ConversationIdSupport.conversationId(second, first));
    }

    @Test
    void conversationIdShouldOrderMostSignificantBitsAsSignedLong() {
        UUID maxPositiveMsb = UUID.fromString("7fffffff-ffff-7fff-bfff-ffffffffffff");
        UUID minNegativeMsb = UUID.fromString("80000000-0000-7000-8000-000000000000");
        String expected = "80000000-0000-7000-8000-000000000000_7fffffff-ffff-7fff-bfff-ffffffffffff";

        assertEquals(expected, ConversationIdSupport.conversationId(maxPositiveMsb, minNegativeMsb));
        assertEquals(expected, ConversationIdSupport.conversationId(minNegativeMsb, maxPositiveMsb));
    }

    @Test
    void conversationIdShouldOrderLeastSignificantBitsAsSignedLong() {
        UUID maxPositiveLsb = UUID.fromString("11111111-1111-7111-7fff-ffffffffffff");
        UUID minNegativeLsb = UUID.fromString("11111111-1111-7111-8000-000000000000");
        String expected = "11111111-1111-7111-8000-000000000000_11111111-1111-7111-7fff-ffffffffffff";

        assertEquals(expected, ConversationIdSupport.conversationId(maxPositiveLsb, minNegativeLsb));
        assertEquals(expected, ConversationIdSupport.conversationId(minNegativeLsb, maxPositiveLsb));
    }
}
