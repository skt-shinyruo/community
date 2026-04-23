package com.nowcoder.community.im.common.command;

import java.util.UUID;

/**
 * Client -> im-realtime -> Kafka -> im-core command.
 *
 * <p>conversationId must be derived as: canonicalUuid1 + "_" + canonicalUuid2.</p>
 */
public record SendPrivateTextCommand(
        String requestId,
        String clientMsgId,
        UUID fromUserId,
        UUID toUserId,
        String conversationId,
        String content,
        long clientSentAtEpochMs
) {
}
