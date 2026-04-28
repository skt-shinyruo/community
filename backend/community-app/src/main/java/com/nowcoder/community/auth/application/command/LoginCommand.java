package com.nowcoder.community.auth.application.command;

public record LoginCommand(
        String username,
        String password,
        String captchaId,
        String captchaCode,
        String clientIp,
        String clientIpSource
) {
}
