package com.nowcoder.community.im.common.ws;

import com.nowcoder.community.im.common.ImContractVersions;
import com.nowcoder.community.im.common.ImJsonContract;
import com.nowcoder.community.im.common.ImSchemaVersion;

@ImJsonContract
public record RejectFrame(
        String type,
        String cmd,
        String clientMsgId,
        String requestId,
        int code,
        String reasonCode,
        String message,
        @ImSchemaVersion
        int schemaVersion
) {

    public RejectFrame {
        schemaVersion = ImContractVersions.requireSupportedSchemaVersion(schemaVersion);
        requireType(type, "reject");
    }

    public RejectFrame(
            String type,
            String cmd,
            String clientMsgId,
            String requestId,
            int code,
            String reasonCode,
            String message
    ) {
        this(type, cmd, clientMsgId, requestId, code, reasonCode, message,
                ImContractVersions.WS_FRAME_VERSION);
    }

    private static void requireType(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("type must be " + expected);
        }
    }
}
