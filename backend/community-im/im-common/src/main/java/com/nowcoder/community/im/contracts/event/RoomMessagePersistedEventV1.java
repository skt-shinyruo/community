package com.nowcoder.community.im.common.event;

/**
 * im-core -> Kafka -> im-realtime event.
 *
 * <p>Room chat is pushed as state-only (no content): clients pull history by seq.</p>
 */
public record RoomMessagePersistedEventV1(
        String eventId,
        long roomId,
        long seq,
        long messageId,
        int fromUserId,
        long createdAtEpochMs
) {
}

