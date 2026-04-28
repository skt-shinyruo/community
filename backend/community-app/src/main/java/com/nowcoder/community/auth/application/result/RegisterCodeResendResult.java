package com.nowcoder.community.auth.application.result;

public record RegisterCodeResendResult(
        boolean issued,
        String maskedEmail,
        String debugEmailCode
) {
}
