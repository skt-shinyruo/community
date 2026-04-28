package com.nowcoder.community.analytics.application;

import com.nowcoder.community.analytics.application.command.AnalyticsRangeQuery;
import com.nowcoder.community.analytics.domain.repository.AnalyticsRepository;
import com.nowcoder.community.analytics.domain.service.AnalyticsDomainService;
import com.nowcoder.community.analytics.exception.AnalyticsErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AnalyticsApplicationServiceTest {

    @Test
    void calculateUvShouldValidateRangeAndDelegateToRepository() {
        AnalyticsRepository repository = mock(AnalyticsRepository.class);
        AnalyticsApplicationService service = new AnalyticsApplicationService(repository, new AnalyticsDomainService(), 31);
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 2);
        when(repository.calculateUv(start, end)).thenReturn(2L);

        assertThat(service.calculateUv(new AnalyticsRangeQuery(start, end))).isEqualTo(2L);

        verify(repository).calculateUv(start, end);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void calculateDauShouldValidateRangeAndDelegateToRepository() {
        AnalyticsRepository repository = mock(AnalyticsRepository.class);
        AnalyticsApplicationService service = new AnalyticsApplicationService(repository, new AnalyticsDomainService(), 31);
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 2);
        when(repository.calculateDau(start, end)).thenReturn(3L);

        assertThat(service.calculateDau(new AnalyticsRangeQuery(start, end))).isEqualTo(3L);

        verify(repository).calculateDau(start, end);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void calculateUvShouldRejectInvalidRangeWithDomainCode() {
        AnalyticsRepository repository = mock(AnalyticsRepository.class);
        AnalyticsApplicationService service = new AnalyticsApplicationService(repository, new AnalyticsDomainService(), 31);
        LocalDate start = LocalDate.of(2026, 1, 2);
        LocalDate end = LocalDate.of(2026, 1, 1);

        assertThatThrownBy(() -> service.calculateUv(new AnalyticsRangeQuery(start, end)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(AnalyticsErrorCode.RANGE_INVALID);
                });
        verifyNoInteractions(repository);
    }
}
