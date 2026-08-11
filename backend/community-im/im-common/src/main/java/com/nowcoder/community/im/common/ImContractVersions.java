package com.nowcoder.community.im.common;

public final class ImContractVersions {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int KAFKA_COMMAND_SCHEMA_VERSION = CURRENT_SCHEMA_VERSION;
    public static final int KAFKA_EVENT_SCHEMA_VERSION = CURRENT_SCHEMA_VERSION;
    public static final int PROJECTION_SCHEMA_VERSION = CURRENT_SCHEMA_VERSION;
    public static final int WS_FRAME_VERSION = CURRENT_SCHEMA_VERSION;

    private ImContractVersions() {
    }

    public static int requireSupportedSchemaVersion(int schemaVersion) {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new ImUnsupportedSchemaVersionException(schemaVersion, CURRENT_SCHEMA_VERSION);
        }
        return schemaVersion;
    }

    /**
     * A missing version is an invalid protocol version, rather than a JSON
     * decoding failure. Keeping that distinction lets callers return the
     * contract-level unsupported-version error consistently across Jackson
     * versions.
     */
    public static int requireSupportedSchemaVersion(Integer schemaVersion) {
        return requireSupportedSchemaVersion(schemaVersion == null ? 0 : schemaVersion);
    }
}
