package com.nowcoder.community.content.application.command;

import java.util.UUID;

public record ReconcilePostMediaReferencesCommand(UUID afterAssetId, int batchSize) {

    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    public ReconcilePostMediaReferencesCommand {
        afterAssetId = afterAssetId == null ? ZERO_UUID : afterAssetId;
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        batchSize = Math.min(500, batchSize);
    }
}
