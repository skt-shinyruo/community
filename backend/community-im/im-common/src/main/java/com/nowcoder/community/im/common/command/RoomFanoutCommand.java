package com.nowcoder.community.im.common.command;

import com.nowcoder.community.im.common.ImContractVersions;
import com.nowcoder.community.im.common.ImJsonContract;
import com.nowcoder.community.im.common.ImSchemaVersion;

import java.util.UUID;

/**
 * im-realtime owner -> Kafka -> target im-realtime worker command.
 */
@ImJsonContract
public record RoomFanoutCommand(
        String targetWorkerId,
        UUID roomId,
        long lastSeq,
        String sourceEventId,
        long createdAtEpochMs,
        @ImSchemaVersion
        int schemaVersion
) {

    public RoomFanoutCommand {
        schemaVersion = ImContractVersions.requireSupportedSchemaVersion(schemaVersion);
    }

    public RoomFanoutCommand(
            String targetWorkerId,
            UUID roomId,
            long lastSeq,
            String sourceEventId,
            long createdAtEpochMs
    ) {
        this(targetWorkerId, roomId, lastSeq, sourceEventId, createdAtEpochMs,
                ImContractVersions.KAFKA_COMMAND_SCHEMA_VERSION);
    }
}
