package com.nowcoder.community.user.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.domain.model.UserRole;
import com.nowcoder.community.user.domain.model.UserAccount;

import java.time.Instant;
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
        UserRole.requireValid(type);
        return normalizedReason;
    }

    public void requireRoleUpdateAllowed(
            UUID actorUserId,
            UUID targetUserId,
            int toType,
            UserAccount actor,
            UserAccount target,
            Instant now
    ) {
        requireActiveAdmin(actor, now);
        if (target == null || target.id() == null) {
            throw new BusinessException(INVALID_ARGUMENT, "目标用户不存在");
        }
        if (targetUserId != null && targetUserId.equals(actorUserId) && toType != UserRole.ADMIN.type()) {
            throw new BusinessException(FORBIDDEN, "不允许降级自己的管理员权限");
        }
    }

    public void requireActiveAdmin(UserAccount actor, Instant now) {
        if (actor == null || actor.id() == null
                || actor.type() != UserRole.ADMIN.type()
                || actor.status() == 0
                || activeBan(actor, now)) {
            throw new BusinessException(FORBIDDEN, "操作者不再具备有效管理员权限");
        }
    }

    private boolean activeBan(UserAccount actor, Instant now) {
        return actor.banUntil() != null && actor.banUntil().isAfter(now == null ? Instant.now() : now);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
