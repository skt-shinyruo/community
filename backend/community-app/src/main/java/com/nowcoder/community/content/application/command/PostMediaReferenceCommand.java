package com.nowcoder.community.content.application.command;

import com.nowcoder.community.content.domain.model.PostMediaReferenceOperation;

import java.util.Objects;
import java.util.UUID;

public record PostMediaReferenceCommand(
        UUID assetId,
        PostMediaReferenceOperation operation,
        long operationVersion,
        UUID actorUserId
) {

    public PostMediaReferenceCommand {
        Objects.requireNonNull(assetId, "assetId must not be null");
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(actorUserId, "actorUserId must not be null");
        if (operationVersion <= 0L) {
            throw new IllegalArgumentException("operationVersion must be positive");
        }
    }
}
