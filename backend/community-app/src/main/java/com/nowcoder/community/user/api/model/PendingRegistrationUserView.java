package com.nowcoder.community.user.api.model;

import java.util.UUID;

public record PendingRegistrationUserView(
        UUID userId,
        String username,
        String email,
        int status,
        int type,
        String headerUrl
) {
}
