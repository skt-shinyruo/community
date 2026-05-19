package com.nowcoder.community.im.realtime.fanout;

import java.util.UUID;

public record RoomFanoutRoute(
        UUID roomId,
        long lastSeq,
        String targetWorkerId
) {
}
