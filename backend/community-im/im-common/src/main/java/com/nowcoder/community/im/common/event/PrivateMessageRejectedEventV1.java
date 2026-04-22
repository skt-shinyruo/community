package com.nowcoder.community.im.common.event;

import java.util.UUID;

/**
 * im-core -> Kafka -> im-realtime event for async send rejection after enqueue.
 */
public record PrivateMessageRejectedEventV1(
        String eventId,
        String requestId,
        String clientMsgId,
        UUID fromUserId,
        UUID toUserId,
        String conversationId,
        int code,
        String reasonCode,
        String message,
        long createdAtEpochMs
) {
}
