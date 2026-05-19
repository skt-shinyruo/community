package com.nowcoder.community.im.common;

public final class ImContractVersions {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int KAFKA_COMMAND_SCHEMA_VERSION = CURRENT_SCHEMA_VERSION;
    public static final int KAFKA_EVENT_SCHEMA_VERSION = CURRENT_SCHEMA_VERSION;
    public static final int PROJECTION_SCHEMA_VERSION = CURRENT_SCHEMA_VERSION;
    public static final int WS_FRAME_VERSION = CURRENT_SCHEMA_VERSION;

    private ImContractVersions() {
    }

    public static int schemaVersionOrCurrent(int schemaVersion) {
        return schemaVersion <= 0 ? CURRENT_SCHEMA_VERSION : schemaVersion;
    }
}
