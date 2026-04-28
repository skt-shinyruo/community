// 审核处置请求：版主/管理员对举报执行处置动作（reject/hide/delete/warn/mute/ban）。
package com.nowcoder.community.content.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class ModerationActionRequest {

    @NotNull
    private UUID reportId;

    @NotBlank
    private String action;

    @NotBlank
    private String reason;

    // mute/ban 使用，单位：秒；未提供时使用服务端默认值。
    private Integer durationSeconds;

    public UUID getReportId() {
        return reportId;
    }

    public void setReportId(UUID reportId) {
        this.reportId = reportId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }
}
