package com.nowcoder.community.auth.application.command;

public record ConfirmPasswordResetCommand(
        String resetToken,
        String newPassword,
        String captchaId,
        String captchaCode
) {
}
