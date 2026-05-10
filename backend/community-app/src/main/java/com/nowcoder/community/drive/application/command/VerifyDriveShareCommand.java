package com.nowcoder.community.drive.application.command;

public record VerifyDriveShareCommand(
        String shareToken,
        String password,
        String visitorFingerprint
) {
}
