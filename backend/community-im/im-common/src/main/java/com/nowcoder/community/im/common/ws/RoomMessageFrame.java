package com.nowcoder.community.im.common.ws;

import com.nowcoder.community.im.common.ImContractVersions;
import com.nowcoder.community.im.common.ImJsonContract;
import com.nowcoder.community.im.common.ImSchemaVersion;

import java.util.UUID;

@ImJsonContract
public record RoomMessageFrame(
        String type,
        UUID roomId,
        long seq,
        UUID messageId,
        UUID fromUserId,
        long createdAtEpochMillis,
        @ImSchemaVersion
        int schemaVersion
) {

    public RoomMessageFrame {
        schemaVersion = ImContractVersions.requireSupportedSchemaVersion(schemaVersion);
        requireType(type, "roomMessage");
    }

    public RoomMessageFrame(
            String type,
            UUID roomId,
            long seq,
            UUID messageId,
            UUID fromUserId,
            long createdAtEpochMillis
    ) {
        this(type, roomId, seq, messageId, fromUserId, createdAtEpochMillis,
                ImContractVersions.WS_FRAME_VERSION);
    }

    private static void requireType(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("type must be " + expected);
        }
    }
}
