package com.nowcoder.community.im.common.command;

/**
 * Client -> im-realtime -> Kafka -> im-core command.
 *
 * <p>conversationId must be derived as: min(fromUserId, toUserId) + "_" + max(fromUserId, toUserId).</p>
 */
public record SendPrivateTextCommandV1(
        String requestId,
        String clientMsgId,
        int fromUserId,
        int toUserId,
        String conversationId,
        String content,
        long clientSentAtEpochMs
) {
}

