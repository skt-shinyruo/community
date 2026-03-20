package com.nowcoder.community.im.realtime.support;

public final class ConversationIdSupport {

    private ConversationIdSupport() {
    }

    public static String conversationId(int fromUserId, int toUserId) {
        int a = Math.min(fromUserId, toUserId);
        int b = Math.max(fromUserId, toUserId);
        return a + "_" + b;
    }
}

