package com.nowcoder.community.im.common;

public final class CurrentSchemaVersionFilter {

    @Override
    public boolean equals(Object other) {
        return other instanceof Integer version && version == ImContractVersions.CURRENT_SCHEMA_VERSION;
    }

    @Override
    public int hashCode() {
        return ImContractVersions.CURRENT_SCHEMA_VERSION;
    }
}
