package com.nowcoder.community.user.domain.model;

public enum RefreshTokenSessionState {
    ACTIVE,
    PENDING_ROTATION,
    CONSUMED,
    REVOKED
}
