package com.nowcoder.community.drive.application.result;

public record DriveUploadRecoveryResult(
        int finalized,
        int markedObjectCompleted,
        int failed,
        int skipped
) {
}
