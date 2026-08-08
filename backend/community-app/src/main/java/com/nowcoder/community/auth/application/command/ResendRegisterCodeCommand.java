package com.nowcoder.community.auth.application.command;

public record ResendRegisterCodeCommand(
        String registrationToken,
        String captchaId,
        String captchaCode,
        String clientIp
) {
    public ResendRegisterCodeCommand(String registrationToken, String captchaId, String captchaCode) {
        this(registrationToken, captchaId, captchaCode, null);
    }
}
