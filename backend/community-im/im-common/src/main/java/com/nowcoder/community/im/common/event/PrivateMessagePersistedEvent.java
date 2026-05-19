package com.nowcoder.community.im.common.event;

import java.util.UUID;

/**
 * im-core -> Kafka -> im-realtime event.
 *
 * <p>Private chat is pushed with content in realtime.</p>
 */
public record PrivateMessagePersistedEvent(
        String eventId,
        String conversationId,
        long seq,
        UUID messageId,
        UUID fromUserId,
        UUID toUserId,
        String content,
        long createdAtEpochMs
) {
}
