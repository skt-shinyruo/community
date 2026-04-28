package com.nowcoder.community.analytics.domain.service;

import com.nowcoder.community.analytics.domain.model.AnalyticsRange;
import com.nowcoder.community.analytics.exception.AnalyticsErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.time.temporal.ChronoUnit;

@Service
public class AnalyticsDomainService {

    public void validateRange(AnalyticsRange range, int maxDaysRange) {
        if (range == null || range.start() == null || range.end() == null) {
            throw new BusinessException(AnalyticsErrorCode.RANGE_INVALID, "start/end 必填");
        }
        if (range.end().isBefore(range.start())) {
            throw new BusinessException(AnalyticsErrorCode.RANGE_INVALID, "end 不能早于 start");
        }
        long days = ChronoUnit.DAYS.between(range.start(), range.end()) + 1;
        if (days > maxDaysRange) {
            throw new BusinessException(AnalyticsErrorCode.RANGE_INVALID, "查询区间过大");
        }
    }
}
