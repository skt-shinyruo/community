package com.nowcoder.community.im.common.command;

import com.nowcoder.community.im.common.ImContractVersions;
import com.nowcoder.community.im.common.ImJsonContract;
import com.nowcoder.community.im.common.ImSchemaVersion;

import java.util.UUID;

/**
 * Client -> im-realtime -> Kafka -> im-core command.
 */
@ImJsonContract
public record SendRoomTextCommand(
        String requestId,
        String clientMsgId,
        UUID fromUserId,
        UUID roomId,
        String content,
        long clientSentAtEpochMs,
        @ImSchemaVersion
        int schemaVersion
) {

    public SendRoomTextCommand {
        schemaVersion = ImContractVersions.schemaVersionOrCurrent(schemaVersion);
    }

    public SendRoomTextCommand(
            String requestId,
            String clientMsgId,
            UUID fromUserId,
            UUID roomId,
            String content,
            long clientSentAtEpochMs
    ) {
        this(requestId, clientMsgId, fromUserId, roomId, content, clientSentAtEpochMs,
                ImContractVersions.KAFKA_COMMAND_SCHEMA_VERSION);
    }
}
