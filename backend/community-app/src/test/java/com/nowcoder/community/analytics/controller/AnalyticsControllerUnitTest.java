package com.nowcoder.community.analytics.controller;

import com.nowcoder.community.analytics.application.AnalyticsApplicationService;
import com.nowcoder.community.analytics.application.command.AnalyticsRangeQuery;
import com.nowcoder.community.common.web.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsControllerUnitTest {

    @Mock
    private AnalyticsApplicationService analyticsApplicationService;

    private AnalyticsController controller;

    @BeforeEach
    void setUp() {
        controller = new AnalyticsController(analyticsApplicationService);
    }

    @Test
    void uvShouldDelegateToAnalyticsApplicationService() {
        LocalDate start = LocalDate.of(2026, 4, 1);
        LocalDate end = LocalDate.of(2026, 4, 24);
        when(analyticsApplicationService.calculateUv(new AnalyticsRangeQuery(start, end))).thenReturn(42L);

        Result<Long> result = controller.uv(start, end);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).isEqualTo(42L);
        verify(analyticsApplicationService).calculateUv(new AnalyticsRangeQuery(start, end));
    }

    @Test
    void dauShouldDelegateToAnalyticsApplicationService() {
        LocalDate start = LocalDate.of(2026, 4, 1);
        LocalDate end = LocalDate.of(2026, 4, 24);
        when(analyticsApplicationService.calculateDau(new AnalyticsRangeQuery(start, end))).thenReturn(7L);

        Result<Long> result = controller.dau(start, end);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).isEqualTo(7L);
        verify(analyticsApplicationService).calculateDau(new AnalyticsRangeQuery(start, end));
    }
}
