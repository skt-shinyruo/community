package com.nowcoder.community.im.realtime.fanout;

import java.util.UUID;

public record RoomFanoutCommand(
        String targetWorkerId,
        UUID roomId,
        long lastSeq,
        String sourceEventId,
        long createdAtEpochMs
) {
}
