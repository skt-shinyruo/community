package com.nowcoder.community.drive.controller.dto;

import com.nowcoder.community.drive.application.result.DriveSharePageResult;

import java.util.List;

public record DriveSharePageResponse(
        List<DriveShareResponse> items,
        boolean hasNext,
        int page,
        int size
) {
    public static DriveSharePageResponse from(DriveSharePageResult result) {
        return new DriveSharePageResponse(
                result.items().stream().map(DriveShareResponse::from).toList(),
                result.hasNext(),
                result.page(),
                result.size()
        );
    }
}
