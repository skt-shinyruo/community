package com.nowcoder.community.drive.controller.dto;

import com.nowcoder.community.drive.application.result.DriveDownloadUrlResult;

import java.time.Instant;
import java.util.UUID;

public record DriveDownloadUrlResponse(
        UUID entryId,
        String url,
        Instant expiresAt
) {
    public static DriveDownloadUrlResponse from(DriveDownloadUrlResult result) {
        if (result == null) {
            return null;
        }
        return new DriveDownloadUrlResponse(result.entryId(), result.url(), result.expiresAt());
    }
}
