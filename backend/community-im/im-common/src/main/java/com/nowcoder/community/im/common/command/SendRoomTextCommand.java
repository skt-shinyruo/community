package com.nowcoder.community.im.common.command;

import java.util.UUID;

/**
 * Client -> im-realtime -> Kafka -> im-core command.
 */
public record SendRoomTextCommand(
        String requestId,
        String clientMsgId,
        UUID fromUserId,
        UUID roomId,
        String content,
        long clientSentAtEpochMs
) {
}
