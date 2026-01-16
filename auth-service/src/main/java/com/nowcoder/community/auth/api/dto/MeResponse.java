package com.nowcoder.community.auth.api.dto;

import java.util.List;

public class MeResponse {

    private int userId;
    private List<String> roles;
    private String traceId;

    public MeResponse() {
    }

    public MeResponse(int userId, List<String> roles, String traceId) {
        this.userId = userId;
        this.roles = roles;
        this.traceId = traceId;
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

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}

