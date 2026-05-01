package com.nowcoder.community.content.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.domain.model.ModerationDecision;

import java.util.Locale;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

public class ModerationDecisionDomainService {

    private static final int DEFAULT_MUTE_SECONDS = 24 * 3600;
    private static final int DEFAULT_BAN_SECONDS = 7 * 24 * 3600;

    public ModerationDecision decide(UUID actorId, UUID reportId, String action, String reason, Integer durationSeconds) {
        if (actorId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorId 非法");
        }
        if (reportId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "reportId 非法");
        }
        String normalizedAction = safeLower(action);
        if (!isSupportedAction(normalizedAction)) {
            throw new BusinessException(INVALID_ARGUMENT, "action 非法");
        }
        String normalizedReason = safeTrim(reason);
        if (normalizedReason.isBlank()) {
            throw new BusinessException(INVALID_ARGUMENT, "reason 不能为空");
        }
        return new ModerationDecision(
                actorId,
                reportId,
                normalizedAction,
                normalizedReason,
                resolveDuration(normalizedAction, durationSeconds)
        );
    }

    private int resolveDuration(String action, Integer durationSeconds) {
        if ("mute".equals(action)) {
            return durationSeconds == null || durationSeconds <= 0 ? DEFAULT_MUTE_SECONDS : durationSeconds;
        }
        if ("ban".equals(action)) {
            return durationSeconds == null || durationSeconds <= 0 ? DEFAULT_BAN_SECONDS : durationSeconds;
        }
        return durationSeconds == null ? 0 : Math.max(0, durationSeconds);
    }

    private boolean isSupportedAction(String action) {
        return "reject".equals(action)
                || "hide".equals(action)
                || "delete".equals(action)
                || "warn".equals(action)
                || "mute".equals(action)
                || "ban".equals(action);
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeLower(String value) {
        return safeTrim(value).toLowerCase(Locale.ROOT);
    }
}
