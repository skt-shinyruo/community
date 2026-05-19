package com.nowcoder.community.im.common.event;

import java.util.UUID;

/**
 * im-core -> Kafka -> im-realtime send-result event for a committed private send attempt.
 */
public record PrivateMessageCommittedEvent(
        String eventId,
        String requestId,
        String clientMsgId,
        UUID fromUserId,
        UUID toUserId,
        String conversationId,
        UUID messageId,
        long seq,
        long createdAtEpochMs
) {
}
