package com.nowcoder.community.auth.application.command;

public record RegisterCommand(
        String username,
        String password,
        String email,
        String captchaId,
        String captchaCode,
        String clientIp
) {
    public RegisterCommand(String username, String password, String email, String captchaId, String captchaCode) {
        this(username, password, email, captchaId, captchaCode, null);
    }
}
