package com.nowcoder.community.im.core.support;

public final class ConversationIdSupport {

    private ConversationIdSupport() {
    }

    public static String conversationId(int userId1, int userId2) {
        int small = Math.min(userId1, userId2);
        int large = Math.max(userId1, userId2);
        return small + "_" + large;
    }
}

