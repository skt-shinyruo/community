package com.nowcoder.community.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public class UpdateUserRoleRequest {

    private UUID targetUserId;

    /**
     * 0=USER, 1=ADMIN, 2=MODERATOR
     */
    @Min(value = 0, message = "type 非法")
    @Max(value = 2, message = "type 非法")
    private int type;

    /**
     * 审计原因：避免误操作与便于追溯。
     */
    @NotBlank(message = "reason 不能为空")
    @Size(max = 200, message = "reason 过长（max=200）")
    private String reason;

    /**
     * 二次确认：客户端需显式传 true。
     */
    private boolean confirm;

    public UUID getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(UUID targetUserId) {
        this.targetUserId = targetUserId;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public boolean isConfirm() {
        return confirm;
    }

    public void setConfirm(boolean confirm) {
        this.confirm = confirm;
    }
}
