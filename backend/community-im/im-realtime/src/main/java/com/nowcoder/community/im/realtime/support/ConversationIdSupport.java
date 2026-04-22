package com.nowcoder.community.im.realtime.support;

import java.util.UUID;

public final class ConversationIdSupport {

    private ConversationIdSupport() {
    }

    public static String conversationId(UUID fromUserId, UUID toUserId) {
        if (fromUserId == null || toUserId == null) {
            throw new IllegalArgumentException("user ids required");
        }
        UUID first = fromUserId.compareTo(toUserId) <= 0 ? fromUserId : toUserId;
        UUID second = first.equals(fromUserId) ? toUserId : fromUserId;
        return first + "_" + second;
    }
}
