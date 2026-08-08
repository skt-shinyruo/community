package com.nowcoder.community.user.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.domain.model.UserAccount;
import com.nowcoder.community.user.domain.model.UserModerationStatus;
import com.nowcoder.community.user.domain.model.UserRole;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

public class UserModerationDomainService {

    private static final int MAX_DURATION_SECONDS = 365 * 24 * 3600;

    public String requireNonBlankAction(String action) {
        String value = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        if (!hasText(value)) {
            throw new BusinessException(INVALID_ARGUMENT, "action 不能为空");
        }
        return value;
    }

    public void requireActiveModerationActor(UserAccount actor, Instant now) {
        if (actor == null || actor.id() == null || actor.status() == 0 || hasActiveBan(actor, now)) {
            throw new BusinessException(FORBIDDEN, "操作者不再具备有效治理权限");
        }
        if (UserRole.requireValid(actor.type()) == UserRole.USER) {
            throw new BusinessException(FORBIDDEN, "普通用户无权执行用户处罚");
        }
    }

    public void requireModerationHierarchy(UserAccount actor, UserAccount target) {
        if (target == null || target.id() == null) {
            throw new BusinessException(INVALID_ARGUMENT, "目标用户不存在");
        }
        if (sameUser(actor.id(), target.id())) {
            throw new BusinessException(FORBIDDEN, "不允许处罚自己");
        }

        UserRole actorRole = UserRole.requireValid(actor.type());
        UserRole targetRole = UserRole.requireValid(target.type());
        if (targetRole == UserRole.ADMIN) {
            throw new BusinessException(FORBIDDEN, "不允许处罚管理员");
        }
        if (actorRole == UserRole.MODERATOR && targetRole != UserRole.USER) {
            throw new BusinessException(FORBIDDEN, "版主只能处罚普通用户");
        }
    }

    public UserModerationStatus applyModeration(
            UserModerationStatus current,
            String action,
            int durationSeconds,
            Instant now
    ) {
        if (current == null || current.userId() == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        Instant basis = now == null ? Instant.now() : now;
        String value = requireNonBlankAction(action);
        int seconds = clampDurationSeconds(durationSeconds);
        Instant muteUntil = current.muteUntil();
        Instant banUntil = current.banUntil();

        if ("mute".equals(value)) {
            muteUntil = seconds <= 0 ? null : basis.plusSeconds(seconds);
        } else if ("ban".equals(value)) {
            banUntil = seconds <= 0 ? null : basis.plusSeconds(seconds);
        } else if ("unmute".equals(value)) {
            muteUntil = null;
        } else if ("unban".equals(value)) {
            banUntil = null;
        } else {
            throw new BusinessException(INVALID_ARGUMENT, "action 非法");
        }

        return new UserModerationStatus(current.userId(), muteUntil, banUntil, 0L);
    }

    private int clampDurationSeconds(int seconds) {
        return Math.min(MAX_DURATION_SECONDS, Math.max(0, seconds));
    }

    private boolean hasActiveBan(UserAccount user, Instant now) {
        Instant basis = now == null ? Instant.now() : now;
        return user.banUntil() != null && user.banUntil().isAfter(basis);
    }

    private boolean sameUser(UUID left, UUID right) {
        return left != null && left.equals(right);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
