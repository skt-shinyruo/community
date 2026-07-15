package com.nowcoder.community.content.domain.model;

public enum PostMediaUploadStatus {
    PREPARED,
    COMPLETING,
    OBJECT_COMPLETED,
    COMPLETED,
    FAILED;

    public boolean canTransitionTo(PostMediaUploadStatus target) {
        if (target == null) {
            return false;
        }
        return switch (this) {
            case PREPARED -> target == COMPLETING;
            case COMPLETING -> target == PREPARED || target == OBJECT_COMPLETED || target == FAILED;
            case OBJECT_COMPLETED -> target == COMPLETED || target == FAILED;
            case COMPLETED, FAILED -> false;
        };
    }
}
