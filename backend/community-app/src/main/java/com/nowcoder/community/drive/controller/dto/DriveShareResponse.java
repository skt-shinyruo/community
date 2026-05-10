package com.nowcoder.community.drive.controller.dto;

import com.nowcoder.community.drive.application.result.DriveShareResult;

import java.time.Instant;
import java.util.UUID;

public record DriveShareResponse(
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
    public static DriveShareResponse from(DriveShareResult result) {
        if (result == null) {
            return null;
        }
        return new DriveShareResponse(
                result.shareId(),
                result.entryId(),
                result.shareToken(),
                result.entryName(),
                result.entryType(),
                result.expiresAt(),
                result.status(),
                result.ticket(),
                result.ticketExpiresAt()
        );
    }
}
