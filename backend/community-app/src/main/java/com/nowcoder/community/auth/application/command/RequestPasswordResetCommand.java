package com.nowcoder.community.auth.application.command;

public record RequestPasswordResetCommand(String email, String captchaId, String captchaCode) {
}
