package com.nowcoder.community.analytics.application;

import com.nowcoder.community.analytics.domain.repository.AnalyticsRepository;
import com.nowcoder.community.analytics.exception.AnalyticsErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Service
public class AnalyticsApplicationService {

    private final AnalyticsRepository analyticsRepository;
    private final int maxDaysRange;

    public AnalyticsApplicationService(
            AnalyticsRepository analyticsRepository,
            @Value("${analytics.max-days-range:31}") int maxDaysRange
    ) {
        this.analyticsRepository = Objects.requireNonNull(analyticsRepository, "analyticsRepository must not be null");
        this.maxDaysRange = maxDaysRange;
    }

    public long calculateUv(DateRange query) {
        validateRange(query.start(), query.end());
        return analyticsRepository.calculateUv(query.start(), query.end());
    }

    public long calculateDau(DateRange query) {
        validateRange(query.start(), query.end());
        return analyticsRepository.calculateDau(query.start(), query.end());
    }

    private void validateRange(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new BusinessException(AnalyticsErrorCode.RANGE_INVALID, "start/end 必填");
        }
        if (end.isBefore(start)) {
            throw new BusinessException(AnalyticsErrorCode.RANGE_INVALID, "end 不能早于 start");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > maxDaysRange) {
            throw new BusinessException(AnalyticsErrorCode.RANGE_INVALID, "查询区间过大");
        }
    }

    public record DateRange(LocalDate start, LocalDate end) {
    }
}
