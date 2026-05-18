package com.nowcoder.community.drive.application.result;

public record DrivePublicShareGateResult(
        String shareToken,
        boolean requiresPassword
) {
}
