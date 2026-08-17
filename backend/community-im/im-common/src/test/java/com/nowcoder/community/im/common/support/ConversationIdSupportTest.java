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
}
