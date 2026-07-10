package com.nowcoder.community.im.common.event;

import com.nowcoder.community.im.common.ImContractVersions;
import com.nowcoder.community.im.common.ImJsonContract;
import com.nowcoder.community.im.common.ImSchemaVersion;

import java.util.UUID;

/**
 * im-core -> Kafka -> im-realtime event.
 *
 * <p>Room chat is pushed as state-only (no content): clients pull history by seq.</p>
 */
@ImJsonContract
public record RoomMessagePersistedEvent(
        String eventId,
        UUID roomId,
        long seq,
        UUID messageId,
        UUID fromUserId,
        long createdAtEpochMs,
        @ImSchemaVersion
        int schemaVersion
) {

    public RoomMessagePersistedEvent {
        schemaVersion = ImContractVersions.requireSupportedSchemaVersion(schemaVersion);
    }

    public RoomMessagePersistedEvent(
            String eventId,
            UUID roomId,
            long seq,
            UUID messageId,
            UUID fromUserId,
            long createdAtEpochMs
    ) {
        this(eventId, roomId, seq, messageId, fromUserId, createdAtEpochMs,
                ImContractVersions.KAFKA_EVENT_SCHEMA_VERSION);
    }
}
