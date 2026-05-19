package com.nowcoder.community.im.core.domain.model;

import java.time.Instant;
import java.util.UUID;

public record RoomMessageRecord(
        UUID roomId,
        long seq,
        UUID messageId,
        UUID fromUserId,
        String content,
        String clientMsgId,
        Instant createdAt
) {
}
