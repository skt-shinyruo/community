package com.nowcoder.community.analytics.application;

import com.nowcoder.community.analytics.domain.model.AnalyticsRange;
import com.nowcoder.community.analytics.domain.repository.AnalyticsRepository;
import com.nowcoder.community.analytics.domain.service.AnalyticsDomainService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Objects;

@Service
public class AnalyticsApplicationService {

    private final AnalyticsRepository analyticsRepository;
    private final AnalyticsDomainService analyticsDomainService;
    private final int maxDaysRange;

    public AnalyticsApplicationService(
            AnalyticsRepository analyticsRepository,
            AnalyticsDomainService analyticsDomainService,
            @Value("${analytics.max-days-range:31}") int maxDaysRange
    ) {
        this.analyticsRepository = Objects.requireNonNull(analyticsRepository, "analyticsRepository must not be null");
        this.analyticsDomainService = Objects.requireNonNull(analyticsDomainService, "analyticsDomainService must not be null");
        this.maxDaysRange = maxDaysRange;
    }

    public long calculateUv(DateRange query) {
        AnalyticsRange range = new AnalyticsRange(query.start(), query.end());
        analyticsDomainService.validateRange(range, maxDaysRange);
        return analyticsRepository.calculateUv(range.start(), range.end());
    }

    public long calculateDau(DateRange query) {
        AnalyticsRange range = new AnalyticsRange(query.start(), query.end());
        analyticsDomainService.validateRange(range, maxDaysRange);
        return analyticsRepository.calculateDau(range.start(), range.end());
    }

    public record DateRange(LocalDate start, LocalDate end) {
    }
}
