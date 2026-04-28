package com.nowcoder.community.analytics.domain.repository;

import java.time.LocalDate;

public interface AnalyticsRepository {

    void recordUv(LocalDate date, String ip);

    long calculateUv(LocalDate start, LocalDate end);

    void recordDau(LocalDate date, int userId);

    long calculateDau(LocalDate start, LocalDate end);
}
