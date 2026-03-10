package com.nowcoder.community.im.contracts.event;

/**
 * im-core -> Kafka -> im-realtime event.
 *
 * <p>Private chat is pushed with content in realtime.</p>
 */
public record PrivateMessagePersistedEventV1(
        String eventId,
        String conversationId,
        long seq,
        long messageId,
        int fromUserId,
        int toUserId,
        String content,
        long createdAtEpochMs
) {
}

