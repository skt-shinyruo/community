package com.nowcoder.community.growth.domain.service;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.Locale;
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
        if (bizDate == null) {
            throw new IllegalArgumentException("bizDate must not be null");
        }
        if (periodType == null || periodType.isBlank()) {
            return String.valueOf(bizDate);
        }
        return switch (periodType.trim()) {
            case "DAILY" -> String.valueOf(bizDate);
            case "WEEKLY" -> {
                WeekFields weekFields = WeekFields.ISO;
                int week = bizDate.get(weekFields.weekOfWeekBasedYear());
                int year = bizDate.get(weekFields.weekBasedYear());
                yield String.format(Locale.ROOT, "%04d-W%02d", year, week);
            }
            case "LIFETIME" -> "LIFETIME";
            default -> throw new IllegalArgumentException("unsupported period type: " + periodType.trim());
        };
    }

    public int cappedDelta(int currentProgress, int targetProgress, int increment) {
        if (targetProgress <= 0) {
            return currentProgress;
        }
        return Math.min(targetProgress, currentProgress + Math.max(0, increment));
    }
}
