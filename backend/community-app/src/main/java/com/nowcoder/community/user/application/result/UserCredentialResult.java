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
    public UserCredentialResult(UUID userId, String username, int status, int type, String headerUrl, long securityVersion) {
        this(userId, username, status, type, headerUrl, securityVersion, status != 0, status != 0);
    }
}
