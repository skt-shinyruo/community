package com.nowcoder.community.im.common.ws;

import com.nowcoder.community.im.common.ImContractVersions;
import com.nowcoder.community.im.common.ImJsonContract;
import com.nowcoder.community.im.common.ImSchemaVersion;

@ImJsonContract
public record AckFrame(
        String type,
        String cmd,
        String clientMsgId,
        String requestId,
        @ImSchemaVersion
        int schemaVersion
) {

    public AckFrame {
        schemaVersion = ImContractVersions.schemaVersionOrCurrent(schemaVersion);
        requireType(type, "ack");
    }

    public AckFrame(String type, String cmd, String clientMsgId, String requestId) {
        this(type, cmd, clientMsgId, requestId, ImContractVersions.WS_FRAME_VERSION);
    }

    private static void requireType(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("type must be " + expected);
        }
    }
}
