package com.nowcoder.community.drive.application.result;

import java.time.Instant;
import java.util.UUID;

public record DriveShareResult(
        UUID shareId,
        UUID entryId,
        String shareToken,
        String entryName,
        String entryType,
        Instant expiresAt,
        String status,
        String ticket,
        Instant ticketExpiresAt
) {
}
