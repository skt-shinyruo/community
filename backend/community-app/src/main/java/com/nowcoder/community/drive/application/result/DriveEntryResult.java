package com.nowcoder.community.drive.application.result;

import java.time.Instant;
import java.util.UUID;

public record DriveEntryResult(
        UUID entryId,
        UUID parentId,
        String type,
        String name,
        long sizeBytes,
        String mimeType,
        String status,
        Instant updatedAt
) {
}
