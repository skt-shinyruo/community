package com.nowcoder.community.auth.domain.model;

public enum RefreshTokenSessionState {
    ACTIVE,
    PENDING_ROTATION,
    CONSUMED,
    REVOKED
}
