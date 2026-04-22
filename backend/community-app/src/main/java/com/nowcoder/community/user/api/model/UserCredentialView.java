package com.nowcoder.community.user.api.model;

import java.util.UUID;

public record UserCredentialView(
        UUID userId,
        String username,
        int status,
        int type,
        String headerUrl
) {
}
