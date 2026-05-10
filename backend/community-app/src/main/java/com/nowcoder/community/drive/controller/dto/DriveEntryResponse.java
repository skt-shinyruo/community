package com.nowcoder.community.drive.controller.dto;

import com.nowcoder.community.drive.application.result.DriveEntryResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DriveEntryResponse(
        UUID entryId,
        UUID parentId,
        String type,
        String name,
        long sizeBytes,
        String mimeType,
        String status,
        Instant updatedAt
) {
    public static DriveEntryResponse from(DriveEntryResult result) {
        if (result == null) {
            return null;
        }
        return new DriveEntryResponse(
                result.entryId(),
                result.parentId(),
                result.type(),
                result.name(),
                result.sizeBytes(),
                result.mimeType(),
                result.status(),
                result.updatedAt()
        );
    }

    public static List<DriveEntryResponse> from(List<DriveEntryResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        return results.stream().map(DriveEntryResponse::from).toList();
    }
}
