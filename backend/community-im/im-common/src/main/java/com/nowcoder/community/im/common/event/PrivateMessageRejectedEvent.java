package com.nowcoder.community.im.common.event;

import com.nowcoder.community.im.common.ImContractVersions;
import com.nowcoder.community.im.common.ImJsonContract;
import com.nowcoder.community.im.common.ImSchemaVersion;

import java.util.UUID;

/**
 * im-core -> Kafka -> im-realtime event for async send rejection after enqueue.
 */
@ImJsonContract
public record PrivateMessageRejectedEvent(
        String eventId,
        String requestId,
        String clientMsgId,
        UUID fromUserId,
        UUID toUserId,
        String conversationId,
        int code,
        String reasonCode,
        String message,
        long createdAtEpochMs,
        @ImSchemaVersion
        int schemaVersion
) {

    public PrivateMessageRejectedEvent {
        schemaVersion = ImContractVersions.schemaVersionOrCurrent(schemaVersion);
    }

    public PrivateMessageRejectedEvent(
            String eventId,
            String requestId,
            String clientMsgId,
            UUID fromUserId,
            UUID toUserId,
            String conversationId,
            int code,
            String reasonCode,
            String message,
            long createdAtEpochMs
    ) {
        this(eventId, requestId, clientMsgId, fromUserId, toUserId, conversationId, code, reasonCode, message, createdAtEpochMs,
                ImContractVersions.KAFKA_EVENT_SCHEMA_VERSION);
    }
}
