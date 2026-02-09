package com.nowcoder.community.user.api.dto;

import jakarta.validation.constraints.NotBlank;

public class InternalRefreshTokenRevokeRequest {

    @NotBlank
    private String tokenHash;

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }
}

