package com.nowcoder.community.oss.application;

import com.nowcoder.community.oss.domain.model.OssObject;

import java.util.Objects;
import java.util.Optional;

public record ObjectDeletionClaimResult(
        OssObject object,
        ObjectDeletionClaim claim
) {

    public ObjectDeletionClaimResult {
        Objects.requireNonNull(object, "object must not be null");
    }

    public Optional<ObjectDeletionClaim> claimedDeletion() {
        return Optional.ofNullable(claim);
    }
}
