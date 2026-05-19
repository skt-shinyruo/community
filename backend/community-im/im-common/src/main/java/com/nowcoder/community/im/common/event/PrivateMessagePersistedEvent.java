package com.nowcoder.community.im.common.event;

import com.nowcoder.community.im.common.ImContractVersions;
import com.nowcoder.community.im.common.ImJsonContract;
import com.nowcoder.community.im.common.ImSchemaVersion;

import java.util.UUID;

/**
 * im-core -> Kafka -> im-realtime event.
 *
 * <p>Private chat is pushed with content in realtime.</p>
 */
@ImJsonContract
public record PrivateMessagePersistedEvent(
        String eventId,
        String conversationId,
        long seq,
        UUID messageId,
        UUID fromUserId,
        UUID toUserId,
        String content,
        long createdAtEpochMs,
        @ImSchemaVersion
        int schemaVersion
) {

    public PrivateMessagePersistedEvent {
        schemaVersion = ImContractVersions.schemaVersionOrCurrent(schemaVersion);
    }

    public PrivateMessagePersistedEvent(
            String eventId,
            String conversationId,
            long seq,
            UUID messageId,
            UUID fromUserId,
            UUID toUserId,
            String content,
            long createdAtEpochMs
    ) {
        this(eventId, conversationId, seq, messageId, fromUserId, toUserId, content, createdAtEpochMs,
                ImContractVersions.KAFKA_EVENT_SCHEMA_VERSION);
    }
}
