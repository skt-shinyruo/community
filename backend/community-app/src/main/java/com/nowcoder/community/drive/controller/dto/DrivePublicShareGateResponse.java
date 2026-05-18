package com.nowcoder.community.drive.controller.dto;

import com.nowcoder.community.drive.application.result.DrivePublicShareGateResult;

public record DrivePublicShareGateResponse(
        String shareToken,
        boolean requiresPassword
) {
    public static DrivePublicShareGateResponse from(DrivePublicShareGateResult result) {
        if (result == null) {
            return null;
        }
        return new DrivePublicShareGateResponse(
                result.shareToken(),
                result.requiresPassword()
        );
    }
}
