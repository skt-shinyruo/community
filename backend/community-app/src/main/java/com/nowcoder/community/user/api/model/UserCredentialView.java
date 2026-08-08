package com.nowcoder.community.user.api.model;

import java.util.UUID;

public record UserCredentialView(
        UUID userId,
        String username,
        String email,
        int status,
        int type,
        String headerUrl,
        long securityVersion,
        boolean loginAllowed,
        boolean refreshAllowed
) {
    public UserCredentialView(
            UUID userId,
            String username,
            int status,
            int type,
            String headerUrl,
            long securityVersion,
            boolean loginAllowed,
            boolean refreshAllowed
    ) {
        this(userId, username, null, status, type, headerUrl, securityVersion, loginAllowed, refreshAllowed);
    }
}
