package com.nowcoder.community.im.core.support;

import java.util.UUID;

public final class ConversationIdSupport {

    private ConversationIdSupport() {
    }

    public static String conversationId(UUID userId1, UUID userId2) {
        if (userId1 == null || userId2 == null) {
            throw new IllegalArgumentException("user ids required");
        }
        UUID first = userId1.compareTo(userId2) <= 0 ? userId1 : userId2;
        UUID second = first.equals(userId1) ? userId2 : userId1;
        return first + "_" + second;
    }
}
