package com.nowcoder.community.im.common.ws;

import com.nowcoder.community.im.common.ImContractVersions;
import com.nowcoder.community.im.common.ImJsonContract;
import com.nowcoder.community.im.common.ImSchemaVersion;

import java.util.UUID;

@ImJsonContract
public record CommittedFrame(
        String type,
        String cmd,
        String clientMsgId,
        String requestId,
        String conversationId,
        UUID roomId,
        UUID messageId,
        Long seq,
        @ImSchemaVersion
        int schemaVersion
) {

    public CommittedFrame {
        schemaVersion = ImContractVersions.schemaVersionOrCurrent(schemaVersion);
        requireType(type, "committed");
    }

    public CommittedFrame(
            String type,
            String cmd,
            String clientMsgId,
            String requestId,
            String conversationId,
            UUID roomId,
            UUID messageId,
            Long seq
    ) {
        this(type, cmd, clientMsgId, requestId, conversationId, roomId, messageId, seq,
                ImContractVersions.WS_FRAME_VERSION);
    }

    private static void requireType(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("type must be " + expected);
        }
    }
}
