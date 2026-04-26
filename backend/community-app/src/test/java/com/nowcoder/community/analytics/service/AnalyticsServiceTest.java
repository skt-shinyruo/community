package com.nowcoder.community.analytics.service;

import com.nowcoder.community.analytics.exception.AnalyticsErrorCode;
import com.nowcoder.community.analytics.repo.AnalyticsRepository;
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

class AnalyticsServiceTest {

    @Test
    void recordUvShouldDelegateToRepository() {
        AnalyticsRepository repository = mock(AnalyticsRepository.class);
        AnalyticsService service = new AnalyticsService(repository, 31);
        LocalDate date = LocalDate.of(2026, 1, 1);

        service.recordUv(date, "1.1.1.1");

        verify(repository).recordUv(date, "1.1.1.1");
        verifyNoMoreInteractions(repository);
    }

    @Test
    void recordDauShouldDelegateToRepository() {
        AnalyticsRepository repository = mock(AnalyticsRepository.class);
        AnalyticsService service = new AnalyticsService(repository, 31);
        LocalDate date = LocalDate.of(2026, 1, 1);

        service.recordDau(date, 123);

        verify(repository).recordDau(date, 123);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void calculateUvShouldValidateRangeAndDelegateToRepository() {
        AnalyticsRepository repository = mock(AnalyticsRepository.class);
        AnalyticsService service = new AnalyticsService(repository, 31);
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 2);
        when(repository.calculateUv(start, end)).thenReturn(2L);

        assertThat(service.calculateUv(start, end)).isEqualTo(2L);

        verify(repository).calculateUv(start, end);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void calculateDauShouldValidateRangeAndDelegateToRepository() {
        AnalyticsRepository repository = mock(AnalyticsRepository.class);
        AnalyticsService service = new AnalyticsService(repository, 31);
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 2);
        when(repository.calculateDau(start, end)).thenReturn(3L);

        assertThat(service.calculateDau(start, end)).isEqualTo(3L);

        verify(repository).calculateDau(start, end);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void calculateUvShouldRejectInvalidRangeWithDomainCode() {
        AnalyticsRepository repository = mock(AnalyticsRepository.class);
        AnalyticsService service = new AnalyticsService(repository, 31);
        LocalDate start = LocalDate.of(2026, 1, 2);
        LocalDate end = LocalDate.of(2026, 1, 1);

        assertThatThrownBy(() -> service.calculateUv(start, end))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(AnalyticsErrorCode.RANGE_INVALID);
                });
        verifyNoInteractions(repository);
    }
}
