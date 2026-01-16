package com.nowcoder.community.auth.api.dto;

import java.util.List;

public class LoginResponse {

    private String tokenType = "Bearer";
    private String accessToken;
    private long expiresInSeconds;
    private int userId;
    private List<String> roles;

    public LoginResponse() {
    }

    public LoginResponse(String accessToken, long expiresInSeconds, int userId, List<String> roles) {
        this.accessToken = accessToken;
        this.expiresInSeconds = expiresInSeconds;
        this.userId = userId;
        this.roles = roles;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public void setExpiresInSeconds(long expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }
}

