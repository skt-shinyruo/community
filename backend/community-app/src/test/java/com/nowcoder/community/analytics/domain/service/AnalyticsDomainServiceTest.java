package com.nowcoder.community.analytics.domain.service;

import com.nowcoder.community.analytics.domain.model.AnalyticsRange;
import com.nowcoder.community.analytics.exception.AnalyticsErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalyticsDomainServiceTest {

    private final AnalyticsDomainService service = new AnalyticsDomainService();

    @Test
    void validateRangeShouldAcceptInclusiveRangeWithinLimit() {
        assertThatCode(() -> service.validateRange(
                new AnalyticsRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)),
                31
        )).doesNotThrowAnyException();
    }

    @Test
    void validateRangeShouldRejectInvalidRange() {
        assertThatThrownBy(() -> service.validateRange(
                new AnalyticsRange(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 1)),
                31
        )).isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(AnalyticsErrorCode.RANGE_INVALID));
    }
}
