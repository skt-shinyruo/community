package com.nowcoder.community.drive.application.result;

import java.util.UUID;

public record DriveSpaceResult(
        UUID spaceId,
        UUID userId,
        long quotaBytes,
        long usedBytes,
        long remainingBytes
) {
}
