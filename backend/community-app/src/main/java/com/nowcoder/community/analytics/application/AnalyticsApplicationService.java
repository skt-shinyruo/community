package com.nowcoder.community.analytics.application;

import com.nowcoder.community.analytics.application.command.AnalyticsRangeQuery;
import com.nowcoder.community.analytics.domain.model.AnalyticsRange;
import com.nowcoder.community.analytics.domain.repository.AnalyticsRepository;
import com.nowcoder.community.analytics.domain.service.AnalyticsDomainService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
        this.analyticsRepository = analyticsRepository;
        this.analyticsDomainService = analyticsDomainService;
        this.maxDaysRange = maxDaysRange;
    }

    public long calculateUv(AnalyticsRangeQuery query) {
        AnalyticsRange range = new AnalyticsRange(query.start(), query.end());
        analyticsDomainService.validateRange(range, maxDaysRange);
        return analyticsRepository.calculateUv(range.start(), range.end());
    }

    public long calculateDau(AnalyticsRangeQuery query) {
        AnalyticsRange range = new AnalyticsRange(query.start(), query.end());
        analyticsDomainService.validateRange(range, maxDaysRange);
        return analyticsRepository.calculateDau(range.start(), range.end());
    }
}
