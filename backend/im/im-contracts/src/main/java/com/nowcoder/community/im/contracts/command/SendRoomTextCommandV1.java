package com.nowcoder.community.im.contracts.command;

/**
 * Client -> im-realtime -> Kafka -> im-core command.
 */
public record SendRoomTextCommandV1(
        String requestId,
        String clientMsgId,
        int fromUserId,
        long roomId,
        String content,
        long clientSentAtEpochMs
) {
}

