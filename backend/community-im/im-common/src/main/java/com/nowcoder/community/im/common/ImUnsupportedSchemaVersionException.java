package com.nowcoder.community.im.common;

public class ImUnsupportedSchemaVersionException extends IllegalArgumentException {

    private final int schemaVersion;
    private final int supportedSchemaVersion;

    public ImUnsupportedSchemaVersionException(int schemaVersion, int supportedSchemaVersion) {
        super("unsupported IM schemaVersion " + schemaVersion + "; supported schemaVersion is "
                + supportedSchemaVersion);
        this.schemaVersion = schemaVersion;
        this.supportedSchemaVersion = supportedSchemaVersion;
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public int supportedSchemaVersion() {
        return supportedSchemaVersion;
    }
}
