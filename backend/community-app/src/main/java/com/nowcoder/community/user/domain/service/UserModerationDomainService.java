package com.nowcoder.community.user.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.domain.model.UserModerationStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Locale;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class UserModerationDomainService {

    private static final int MAX_DURATION_SECONDS = 365 * 24 * 3600;

    public String requireNonBlankAction(String action) {
        String value = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(INVALID_ARGUMENT, "action 不能为空");
        }
        return value;
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

        return new UserModerationStatus(current.userId(), muteUntil, banUntil);
    }

    private int clampDurationSeconds(int seconds) {
        return Math.min(MAX_DURATION_SECONDS, Math.max(0, seconds));
    }
}
