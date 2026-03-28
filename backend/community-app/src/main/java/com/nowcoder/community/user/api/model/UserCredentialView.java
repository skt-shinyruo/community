package com.nowcoder.community.user.api.model;

public record UserCredentialView(
        int userId,
        String username,
        int status,
        int type,
        String headerUrl
) {
}
