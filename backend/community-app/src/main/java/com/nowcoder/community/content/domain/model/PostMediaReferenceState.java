package com.nowcoder.community.content.domain.model;

import java.util.Date;
import java.util.Objects;

public record PostMediaReferenceState(
        PostMediaReferenceStatus status,
        long operationVersion,
        Date updatedAt
) {

    public PostMediaReferenceState {
        Objects.requireNonNull(status, "status must not be null");
        if (operationVersion < 0L) {
            throw new IllegalArgumentException("operationVersion must not be negative");
        }
        updatedAt = copy(updatedAt);
    }

    @Override
    public Date updatedAt() {
        return copy(updatedAt);
    }

    public PostMediaReferenceState requestBind(Date now) {
        requireStatus(PostMediaReferenceStatus.UNBOUND);
        return new PostMediaReferenceState(
                PostMediaReferenceStatus.BIND_PENDING,
                nextVersion(),
                requiredTime(now)
        );
    }

    public PostMediaReferenceState markBound(long expectedVersion, Date now) {
        requireStatus(PostMediaReferenceStatus.BIND_PENDING);
        requireCurrentVersion(expectedVersion);
        return new PostMediaReferenceState(
                PostMediaReferenceStatus.BOUND,
                operationVersion,
                requiredTime(now)
        );
    }

    public PostMediaReferenceState requestRelease(Date now) {
        if (status != PostMediaReferenceStatus.BOUND && status != PostMediaReferenceStatus.BIND_PENDING) {
            throw new IllegalStateException(
                    "media reference state must be BOUND or BIND_PENDING but was " + status
            );
        }
        return new PostMediaReferenceState(
                PostMediaReferenceStatus.RELEASE_PENDING,
                nextVersion(),
                requiredTime(now)
        );
    }

    public PostMediaReferenceState markReleased(long expectedVersion, Date now) {
        requireStatus(PostMediaReferenceStatus.RELEASE_PENDING);
        requireCurrentVersion(expectedVersion);
        return new PostMediaReferenceState(
                PostMediaReferenceStatus.RELEASED,
                operationVersion,
                requiredTime(now)
        );
    }

    private long nextVersion() {
        if (operationVersion == Long.MAX_VALUE) {
            throw new IllegalStateException("media reference operation version exhausted");
        }
        return operationVersion + 1L;
    }

    private void requireStatus(PostMediaReferenceStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("media reference state must be " + expected + " but was " + status);
        }
    }

    private void requireCurrentVersion(long expectedVersion) {
        if (operationVersion != expectedVersion) {
            throw new IllegalStateException(
                    "stale media reference operation version: expected=" + expectedVersion + ", current=" + operationVersion
            );
        }
    }

    private static Date requiredTime(Date value) {
        return Objects.requireNonNull(copy(value), "updatedAt must not be null");
    }

    private static Date copy(Date value) {
        return value == null ? null : new Date(value.getTime());
    }
}
