package com.nowcoder.community.im.common.event;

import java.util.UUID;

/**
 * im-core -> Kafka -> im-realtime send-result event for a committed room send attempt.
 */
public record RoomMessageCommittedEvent(
        String eventId,
        String requestId,
        String clientMsgId,
        UUID fromUserId,
        UUID roomId,
        UUID messageId,
        long seq,
        long createdAtEpochMs
) {
}
