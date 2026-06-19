package com.nowcoder.community.user.api.model;

import java.util.UUID;

public record UserCredentialView(
        UUID userId,
        String username,
        int status,
        int type,
        String headerUrl,
        long securityVersion,
        boolean loginAllowed,
        boolean refreshAllowed
) {
    public UserCredentialView(UUID userId, String username, int status, int type, String headerUrl, long securityVersion) {
        this(userId, username, status, type, headerUrl, securityVersion, status != 0, status != 0);
    }
}
