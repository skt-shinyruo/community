package com.nowcoder.community.im.common.ws;

import com.nowcoder.community.im.common.ImContractVersions;
import com.nowcoder.community.im.common.ImJsonContract;
import com.nowcoder.community.im.common.ImSchemaVersion;

@ImJsonContract
public record PingFrame(
        String type,
        long sentAtEpochMillis,
        @ImSchemaVersion
        int schemaVersion
) {

    public PingFrame {
        schemaVersion = ImContractVersions.requireSupportedSchemaVersion(schemaVersion);
        requireType(type, "ping");
    }

    public PingFrame(String type, long sentAtEpochMillis) {
        this(type, sentAtEpochMillis, ImContractVersions.WS_FRAME_VERSION);
    }

    private static void requireType(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("type must be " + expected);
        }
    }
}
