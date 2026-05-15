package com.nowcoder.community.auth.application.command;

public record RequestPasswordResetCommand(String email, String captchaId, String captchaCode, String clientIp) {

    public RequestPasswordResetCommand(String email, String captchaId, String captchaCode) {
        this(email, captchaId, captchaCode, null);
    }
}
