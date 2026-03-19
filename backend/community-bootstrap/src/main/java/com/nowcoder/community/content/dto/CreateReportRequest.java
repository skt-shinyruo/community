// 创建举报请求：支持对帖子/评论/用户提交举报（reason 必填，detail 可选）。
package com.nowcoder.community.content.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class CreateReportRequest {

    @NotBlank
    private String targetType;

    @Min(1)
    private Integer targetId;

    @NotBlank
    private String reason;

    private String detail;

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public Integer getTargetId() {
        return targetId;
    }

    public void setTargetId(Integer targetId) {
        this.targetId = targetId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}

