package com.nowcoder.community.ops.controller.dto;

import jakarta.validation.constraints.NotBlank;

public class HotCacheDegradationRequest {

    private boolean degraded;

    @NotBlank
    private String reason;

    public boolean isDegraded() {
        return degraded;
    }

    public void setDegraded(boolean degraded) {
        this.degraded = degraded;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
