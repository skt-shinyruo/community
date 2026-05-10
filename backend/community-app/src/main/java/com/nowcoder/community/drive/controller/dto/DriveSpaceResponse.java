package com.nowcoder.community.drive.controller.dto;

import com.nowcoder.community.drive.application.result.DriveSpaceResult;

import java.util.UUID;

public record DriveSpaceResponse(
        UUID spaceId,
        UUID userId,
        long quotaBytes,
        long usedBytes,
        long remainingBytes
) {
    public static DriveSpaceResponse from(DriveSpaceResult result) {
        if (result == null) {
            return null;
        }
        return new DriveSpaceResponse(
                result.spaceId(),
                result.userId(),
                result.quotaBytes(),
                result.usedBytes(),
                result.remainingBytes()
        );
    }
}
