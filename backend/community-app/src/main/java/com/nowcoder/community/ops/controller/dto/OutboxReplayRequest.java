package com.nowcoder.community.ops.controller.dto;

import jakarta.validation.constraints.NotBlank;

public class OutboxReplayRequest {

    @NotBlank
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
