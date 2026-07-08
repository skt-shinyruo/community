package com.nowcoder.community.user.application.result;

import java.util.UUID;

public record UserCredentialResult(
        UUID userId,
        String username,
        int status,
        int type,
        String headerUrl,
        long securityVersion,
        boolean loginAllowed,
        boolean refreshAllowed
) {
}
