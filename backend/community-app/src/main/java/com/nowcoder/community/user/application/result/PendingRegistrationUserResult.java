package com.nowcoder.community.user.application.result;

import java.util.UUID;

public record PendingRegistrationUserResult(
        UUID userId,
        String username,
        String email,
        int status,
        int type,
        String headerUrl
) {
}
