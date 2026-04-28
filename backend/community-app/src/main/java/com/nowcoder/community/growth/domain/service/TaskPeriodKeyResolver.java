package com.nowcoder.community.growth.domain.service;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.Locale;

final class TaskPeriodKeyResolver {

    private static final String PERIOD_LIFETIME = "LIFETIME";

    private TaskPeriodKeyResolver() {
    }

    static String resolve(String periodType, LocalDate bizDate) {
        if (bizDate == null) {
            throw new IllegalArgumentException("bizDate must not be null");
        }
        if (periodType == null || periodType.isBlank()) {
            return String.valueOf(bizDate);
        }
        return switch (periodType.trim()) {
            case "DAILY" -> String.valueOf(bizDate);
            case "WEEKLY" -> {
                WeekFields wf = WeekFields.ISO;
                int week = bizDate.get(wf.weekOfWeekBasedYear());
                int year = bizDate.get(wf.weekBasedYear());
                yield String.format(Locale.ROOT, "%04d-W%02d", year, week);
            }
            case "LIFETIME" -> PERIOD_LIFETIME;
            default -> String.valueOf(bizDate);
        };
    }
}
