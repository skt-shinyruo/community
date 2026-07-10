package com.nowcoder.community.im.common.command;

import com.nowcoder.community.im.common.ImContractVersions;
import com.nowcoder.community.im.common.ImJsonContract;
import com.nowcoder.community.im.common.ImSchemaVersion;

import java.util.UUID;

/**
 * Client -> im-realtime -> Kafka -> im-core command.
 *
 * <p>conversationId must be derived as: canonicalUuid1 + "_" + canonicalUuid2.</p>
 */
@ImJsonContract
public record SendPrivateTextCommand(
        String requestId,
        String clientMsgId,
        UUID fromUserId,
        UUID toUserId,
        String conversationId,
        String content,
        long clientSentAtEpochMs,
        @ImSchemaVersion
        int schemaVersion
) {

    public SendPrivateTextCommand {
        schemaVersion = ImContractVersions.requireSupportedSchemaVersion(schemaVersion);
    }

    public SendPrivateTextCommand(
            String requestId,
            String clientMsgId,
            UUID fromUserId,
            UUID toUserId,
            String conversationId,
            String content,
            long clientSentAtEpochMs
    ) {
        this(requestId, clientMsgId, fromUserId, toUserId, conversationId, content, clientSentAtEpochMs,
                ImContractVersions.KAFKA_COMMAND_SCHEMA_VERSION);
    }
}
