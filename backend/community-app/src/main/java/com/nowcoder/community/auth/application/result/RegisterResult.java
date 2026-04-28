package com.nowcoder.community.auth.application.result;

import java.util.UUID;

public record RegisterResult(
        UUID userId,
        String registrationToken,
        boolean emailCodeIssued,
        String maskedEmail,
        String debugEmailCode
) {
}
