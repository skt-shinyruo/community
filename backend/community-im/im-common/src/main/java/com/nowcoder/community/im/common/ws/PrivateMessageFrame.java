package com.nowcoder.community.im.common.ws;

import com.nowcoder.community.im.common.ImContractVersions;
import com.nowcoder.community.im.common.ImJsonContract;
import com.nowcoder.community.im.common.ImSchemaVersion;

import java.util.UUID;

@ImJsonContract
public record PrivateMessageFrame(
        String type,
        String conversationId,
        long seq,
        UUID messageId,
        UUID fromUserId,
        UUID toUserId,
        String content,
        long createdAtEpochMillis,
        @ImSchemaVersion
        int schemaVersion
) {

    public PrivateMessageFrame {
        schemaVersion = ImContractVersions.schemaVersionOrCurrent(schemaVersion);
        requireType(type, "privateMessage");
    }

    public PrivateMessageFrame(
            String type,
            String conversationId,
            long seq,
            UUID messageId,
            UUID fromUserId,
            UUID toUserId,
            String content,
            long createdAtEpochMillis
    ) {
        this(type, conversationId, seq, messageId, fromUserId, toUserId, content, createdAtEpochMillis,
                ImContractVersions.WS_FRAME_VERSION);
    }

    private static void requireType(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("type must be " + expected);
        }
    }
}
