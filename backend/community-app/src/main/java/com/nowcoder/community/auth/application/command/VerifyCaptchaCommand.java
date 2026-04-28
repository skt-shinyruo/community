package com.nowcoder.community.auth.application.command;

public record VerifyCaptchaCommand(String captchaId, String code) {
}
