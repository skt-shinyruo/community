package com.nowcoder.community.user.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.domain.model.UserAccount;

import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

public class UserRoleDomainService {

    public String requireValidCommand(boolean commandPresent, UUID targetUserId, int type, String reason, boolean confirm) {
        if (!commandPresent) {
            throw new BusinessException(INVALID_ARGUMENT, "request 不能为空");
        }
        if (!confirm) {
            throw new BusinessException(INVALID_ARGUMENT, "需要二次确认（confirm=true）");
        }
        String normalizedReason = hasText(reason) ? reason.trim() : "";
        if (!hasText(normalizedReason)) {
            throw new BusinessException(INVALID_ARGUMENT, "reason 不能为空");
        }
        if (targetUserId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "targetUserId 非法");
        }
        return normalizedReason;
    }

    public void requireRoleUpdateAllowed(UUID actorUserId, UUID targetUserId, int toType, UserAccount target) {
        if (target == null || target.id() == null) {
            throw new BusinessException(INVALID_ARGUMENT, "目标用户不存在");
        }
        if (targetUserId != null && targetUserId.equals(actorUserId) && toType != 1) {
            throw new BusinessException(FORBIDDEN, "不允许降级自己的管理员权限");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
