package com.nowcoder.community.drive.domain.model;

public enum DriveUploadStatus {
    PREPARING,
    PREPARED,
    COMPLETING,
    OBJECT_COMPLETED,
    CLEANUP_PENDING,
    COMPLETED,
    FAILED,
    EXPIRED
}
