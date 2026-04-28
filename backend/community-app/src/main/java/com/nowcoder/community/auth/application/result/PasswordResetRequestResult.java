package com.nowcoder.community.auth.application.result;

public record PasswordResetRequestResult(boolean issued, String resetLink) {
}
