package com.nowcoder.community.im.common.event;

import java.util.UUID;

/**
 * Room membership change event, used by im-realtime to keep local indexes updated.
 */
public record RoomMemberChangedEventV1(
        String eventId,
        UUID roomId,
        UUID userId,
        String action,
        long occurredAtEpochMs
) {
}
