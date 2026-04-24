package com.nowcoder.community.analytics.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class AnalyticsApplicationService {

    private final AnalyticsService analyticsService;

    public AnalyticsApplicationService(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    public long calculateUv(LocalDate start, LocalDate end) {
        return analyticsService.calculateUv(start, end);
    }

    public long calculateDau(LocalDate start, LocalDate end) {
        return analyticsService.calculateDau(start, end);
    }
}
