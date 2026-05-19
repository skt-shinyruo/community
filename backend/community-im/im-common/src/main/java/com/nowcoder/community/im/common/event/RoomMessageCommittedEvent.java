package com.nowcoder.community.im.common.event;

import com.nowcoder.community.im.common.ImContractVersions;
import com.nowcoder.community.im.common.ImJsonContract;
import com.nowcoder.community.im.common.ImSchemaVersion;

import java.util.UUID;

/**
 * im-core -> Kafka -> im-realtime send-result event for a committed room send attempt.
 */
@ImJsonContract
public record RoomMessageCommittedEvent(
        String eventId,
        String requestId,
        String clientMsgId,
        UUID fromUserId,
        UUID roomId,
        UUID messageId,
        long seq,
        long createdAtEpochMs,
        @ImSchemaVersion
        int schemaVersion
) {

    public RoomMessageCommittedEvent {
        schemaVersion = ImContractVersions.schemaVersionOrCurrent(schemaVersion);
    }

    public RoomMessageCommittedEvent(
            String eventId,
            String requestId,
            String clientMsgId,
            UUID fromUserId,
            UUID roomId,
            UUID messageId,
            long seq,
            long createdAtEpochMs
    ) {
        this(eventId, requestId, clientMsgId, fromUserId, roomId, messageId, seq, createdAtEpochMs,
                ImContractVersions.KAFKA_EVENT_SCHEMA_VERSION);
    }
}
