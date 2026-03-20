// 内部治理接口请求：用于禁言/封禁/解除（开发阶段默认放行；生产建议通过网络隔离/网关策略收敛暴露面）。
package com.nowcoder.community.user.dto;

import jakarta.validation.constraints.NotBlank;

public class InternalModerationApplyRequest {

    @NotBlank
    private String action;

    // 单位：秒；0 表示按 action 语义解除（如 unmute/unban），或由调用方决定默认值。
    private int durationSeconds;

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }
}
