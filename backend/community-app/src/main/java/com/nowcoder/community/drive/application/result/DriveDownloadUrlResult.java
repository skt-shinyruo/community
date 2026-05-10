package com.nowcoder.community.drive.application.result;

import java.time.Instant;
import java.util.UUID;

public record DriveDownloadUrlResult(
        UUID entryId,
        String url,
        Instant expiresAt
) {
}
