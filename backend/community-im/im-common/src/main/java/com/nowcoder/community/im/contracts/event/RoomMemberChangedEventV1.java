package com.nowcoder.community.im.common.event;

/**
 * Room membership change event, used by im-realtime to keep local indexes updated.
 */
public record RoomMemberChangedEventV1(
        String eventId,
        long roomId,
        int userId,
        String action,
        long occurredAtEpochMs
) {
}

