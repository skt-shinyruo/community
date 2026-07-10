package com.nowcoder.community.im.common.projection;

import com.nowcoder.community.im.common.ImContractVersions;
import com.nowcoder.community.im.common.ImJsonContract;
import com.nowcoder.community.im.common.ImSchemaVersion;

import java.util.UUID;

@ImJsonContract
public record UserBlockRelationEntry(
        UUID blockerUserId,
        UUID blockedUserId,
        boolean active,
        Long version,
        Long occurredAtEpochMillis,
        @ImSchemaVersion
        int schemaVersion
) {

    public UserBlockRelationEntry {
        schemaVersion = ImContractVersions.requireSupportedSchemaVersion(schemaVersion);
        version = ProjectionVersions.requirePositive(version, "version");
    }

    public UserBlockRelationEntry(
            UUID blockerUserId,
            UUID blockedUserId,
            boolean active,
            Long version,
            Long occurredAtEpochMillis
    ) {
        this(blockerUserId, blockedUserId, active, version, occurredAtEpochMillis,
                ImContractVersions.PROJECTION_SCHEMA_VERSION);
    }

}
