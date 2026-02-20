package com.nowcoder.community.analytics.service;

import com.nowcoder.community.analytics.repo.InMemoryAnalyticsRepository;
import com.nowcoder.community.analytics.api.AnalyticsErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalyticsServiceTest {

    @Test
    void uvAndDauShouldWorkInMemory() {
        AnalyticsService service = new AnalyticsService(new InMemoryAnalyticsRepository(), 31);

        LocalDate d1 = LocalDate.of(2026, 1, 1);
        LocalDate d2 = LocalDate.of(2026, 1, 2);

        service.recordUv(d1, "1.1.1.1");
        service.recordUv(d1, "1.1.1.1");
        service.recordUv(d2, "2.2.2.2");

        assertThat(service.calculateUv(d1, d2)).isEqualTo(2);

        service.recordDau(d1, 1);
        service.recordDau(d1, 2);
        service.recordDau(d2, 2);

        assertThat(service.calculateDau(d1, d2)).isEqualTo(2);
    }

    @Test
    void calculateUvShouldRejectInvalidRangeWithDomainCode() {
        AnalyticsService service = new AnalyticsService(new InMemoryAnalyticsRepository(), 31);
        LocalDate start = LocalDate.of(2026, 1, 2);
        LocalDate end = LocalDate.of(2026, 1, 1);

        assertThatThrownBy(() -> service.calculateUv(start, end))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(AnalyticsErrorCode.RANGE_INVALID);
                });
    }
}
