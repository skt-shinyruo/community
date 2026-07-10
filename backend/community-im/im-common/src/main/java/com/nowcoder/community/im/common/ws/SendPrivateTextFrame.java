package com.nowcoder.community.im.common.ws;

import com.nowcoder.community.im.common.ImContractVersions;
import com.nowcoder.community.im.common.ImJsonContract;
import com.nowcoder.community.im.common.ImSchemaVersion;

import java.util.UUID;

@ImJsonContract
public record SendPrivateTextFrame(
        String type,
        String clientMsgId,
        UUID toUserId,
        String content,
        @ImSchemaVersion
        int schemaVersion
) {

    public SendPrivateTextFrame {
        schemaVersion = ImContractVersions.requireSupportedSchemaVersion(schemaVersion);
        requireType(type, "sendPrivateText");
    }

    public SendPrivateTextFrame(String type, String clientMsgId, UUID toUserId, String content) {
        this(type, clientMsgId, toUserId, content, ImContractVersions.WS_FRAME_VERSION);
    }

    private static void requireType(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("type must be " + expected);
        }
    }
}
