package com.nowcoder.community.im.common.ws;

import com.nowcoder.community.im.common.ImContractVersions;
import com.nowcoder.community.im.common.ImJsonContract;
import com.nowcoder.community.im.common.ImSchemaVersion;

@ImJsonContract
public record ConnectFrame(
        String type,
        String ticket,
        @ImSchemaVersion
        int schemaVersion
) {

    public ConnectFrame {
        schemaVersion = ImContractVersions.requireSupportedSchemaVersion(schemaVersion);
        requireType(type, "connect");
    }

    public ConnectFrame(String type, String ticket) {
        this(type, ticket, ImContractVersions.WS_FRAME_VERSION);
    }

    private static void requireType(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("type must be " + expected);
        }
    }
}
