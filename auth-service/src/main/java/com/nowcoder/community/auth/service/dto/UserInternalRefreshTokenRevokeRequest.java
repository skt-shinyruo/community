package com.nowcoder.community.auth.service.dto;

public class UserInternalRefreshTokenRevokeRequest {

    private String tokenHash;

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }
}

