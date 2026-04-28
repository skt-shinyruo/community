package com.nowcoder.community.auth.application.command;

public record RegisterCommand(
        String username,
        String password,
        String email,
        String captchaId,
        String captchaCode
) {
}
