package com.nowcoder.community.im.common.event;

import com.nowcoder.community.im.common.ImContractVersions;
import com.nowcoder.community.im.common.ImJsonContract;
import com.nowcoder.community.im.common.ImSchemaVersion;

import java.util.UUID;

/**
 * im-core -> Kafka -> im-realtime send-result event for a committed private send attempt.
 */
@ImJsonContract
public record PrivateMessageCommittedEvent(
        String eventId,
        String requestId,
        String clientMsgId,
        UUID fromUserId,
        UUID toUserId,
        String conversationId,
        UUID messageId,
        long seq,
        long createdAtEpochMs,
        @ImSchemaVersion
        int schemaVersion
) {

    public PrivateMessageCommittedEvent {
        schemaVersion = ImContractVersions.schemaVersionOrCurrent(schemaVersion);
    }

    public PrivateMessageCommittedEvent(
            String eventId,
            String requestId,
            String clientMsgId,
            UUID fromUserId,
            UUID toUserId,
            String conversationId,
            UUID messageId,
            long seq,
            long createdAtEpochMs
    ) {
        this(eventId, requestId, clientMsgId, fromUserId, toUserId, conversationId, messageId, seq, createdAtEpochMs,
                ImContractVersions.KAFKA_EVENT_SCHEMA_VERSION);
    }
}
