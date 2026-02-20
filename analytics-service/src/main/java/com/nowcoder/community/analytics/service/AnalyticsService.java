package com.nowcoder.community.analytics.service;

import com.nowcoder.community.analytics.repo.AnalyticsRepository;
import com.nowcoder.community.analytics.api.AnalyticsErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class AnalyticsService {

    private final AnalyticsRepository repository;
    private final int maxDaysRange;

    public AnalyticsService(AnalyticsRepository repository, @Value("${analytics.max-days-range:31}") int maxDaysRange) {
        this.repository = repository;
        this.maxDaysRange = maxDaysRange;
    }

    public void recordUv(LocalDate date, String ip) {
        repository.recordUv(date, ip);
    }

    public void recordDau(LocalDate date, int userId) {
        repository.recordDau(date, userId);
    }

    public long calculateUv(LocalDate start, LocalDate end) {
        validateRange(start, end);
        return repository.calculateUv(start, end);
    }

    public long calculateDau(LocalDate start, LocalDate end) {
        validateRange(start, end);
        return repository.calculateDau(start, end);
    }

    private void validateRange(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new BusinessException(AnalyticsErrorCode.RANGE_INVALID, "start/end 必填");
        }
        if (end.isBefore(start)) {
            throw new BusinessException(AnalyticsErrorCode.RANGE_INVALID, "end 不能早于 start");
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        if (days > maxDaysRange) {
            throw new BusinessException(AnalyticsErrorCode.RANGE_INVALID, "查询区间过大");
        }
    }
}
