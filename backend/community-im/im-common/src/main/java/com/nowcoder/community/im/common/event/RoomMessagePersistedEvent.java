package com.nowcoder.community.im.common.event;

import java.util.UUID;

/**
 * im-core -> Kafka -> im-realtime event.
 *
 * <p>Room chat is pushed as state-only (no content): clients pull history by seq.</p>
 */
public record RoomMessagePersistedEvent(
        String eventId,
        UUID roomId,
        long seq,
        UUID messageId,
        UUID fromUserId,
        long createdAtEpochMs
) {
}
