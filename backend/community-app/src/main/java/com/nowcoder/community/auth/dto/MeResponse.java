package com.nowcoder.community.auth.dto;

import java.util.List;
import java.util.UUID;

public class MeResponse {

    private UUID userId;
    private String username;
    private List<String> authorities;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public List<String> getAuthorities() {
        return authorities;
    }

    public void setAuthorities(List<String> authorities) {
        this.authorities = authorities;
    }
}
