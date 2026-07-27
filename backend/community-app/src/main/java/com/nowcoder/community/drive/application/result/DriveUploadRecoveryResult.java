package com.nowcoder.community.drive.application.result;

public record DriveUploadRecoveryResult(
        int prepared,
        int finalized,
        int markedObjectCompleted,
        int failed,
        int skipped
) {

    public DriveUploadRecoveryResult(int finalized, int markedObjectCompleted, int failed, int skipped) {
        this(0, finalized, markedObjectCompleted, failed, skipped);
    }
}
