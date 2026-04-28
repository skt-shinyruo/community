package com.nowcoder.community.auth.application.command;

public record ResendRegisterCodeCommand(
        String registrationToken,
        String captchaId,
        String captchaCode
) {
}
