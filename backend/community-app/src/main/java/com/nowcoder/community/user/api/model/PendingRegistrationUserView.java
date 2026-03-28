package com.nowcoder.community.user.api.model;

public record PendingRegistrationUserView(
        int userId,
        String username,
        String email,
        int status,
        int type,
        String headerUrl
) {
}
