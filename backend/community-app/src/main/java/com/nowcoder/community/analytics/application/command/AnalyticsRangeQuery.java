package com.nowcoder.community.analytics.application.command;

import java.time.LocalDate;

public record AnalyticsRangeQuery(LocalDate start, LocalDate end) {
}
