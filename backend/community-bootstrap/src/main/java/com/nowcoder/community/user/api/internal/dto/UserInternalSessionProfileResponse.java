package com.nowcoder.community.user.api.internal.dto;

import java.io.Serializable;
import java.util.List;

public class UserInternalSessionProfileResponse implements Serializable {

    private int userId;
    private String username;
    private int status;
    private List<String> authorities;

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public List<String> getAuthorities() {
        return authorities == null ? List.of() : authorities;
    }

    public void setAuthorities(List<String> authorities) {
        this.authorities = authorities;
    }
}
