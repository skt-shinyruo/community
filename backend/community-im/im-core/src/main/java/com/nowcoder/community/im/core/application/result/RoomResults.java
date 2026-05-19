package com.nowcoder.community.im.core.application.result;

import java.util.List;
import java.util.UUID;

public final class RoomResults {

    private RoomResults() {
    }

    public record Created(UUID roomId) {
    }

    public record Messages(
            UUID roomId,
            List<MessageItem> items,
            long nextAfterSeq,
            long lastReadSeq
    ) {
    }

    public record MessageItem(
            UUID roomId,
            long seq,
            UUID messageId,
            UUID fromUserId,
            String content,
            String clientMsgId,
            long createdAtEpochMs
    ) {
    }
}
