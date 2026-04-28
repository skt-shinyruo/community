package com.nowcoder.community.growth.domain.service;

import java.time.LocalDate;
import java.util.UUID;

public final class TaskProgressDomainService {

    public boolean isProcessableEvent(UUID userId, String triggerEventType, String sourceEventId, LocalDate bizDate) {
        return userId != null
                && triggerEventType != null
                && !triggerEventType.isBlank()
                && sourceEventId != null
                && !sourceEventId.isBlank()
                && bizDate != null;
    }

    public String periodKey(String periodType, LocalDate bizDate) {
        return TaskPeriodKeyResolver.resolve(periodType, bizDate);
    }

    public int cappedDelta(int currentProgress, int targetProgress, int increment) {
        if (targetProgress <= 0) {
            return currentProgress;
        }
        return Math.min(targetProgress, currentProgress + Math.max(0, increment));
    }
}
