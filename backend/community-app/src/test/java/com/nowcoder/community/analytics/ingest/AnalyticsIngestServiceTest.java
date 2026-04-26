package com.nowcoder.community.analytics.ingest;

import com.nowcoder.community.analytics.repo.AnalyticsUserOrdinalRepository;
import com.nowcoder.community.analytics.service.AnalyticsService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AnalyticsIngestServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-04-26T01:02:03Z"), ZoneOffset.UTC);

    @Test
    void shouldRecordUvAndDauForRequest() {
        AnalyticsService analyticsService = mock(AnalyticsService.class);
        AnalyticsUserOrdinalRepository ordinalRepository = mock(AnalyticsUserOrdinalRepository.class);
        when(ordinalRepository.resolveOrdinal(UUID.fromString("11111111-1111-1111-1111-111111111111"))).thenReturn(9);
        AnalyticsIngestService service = new AnalyticsIngestService(analyticsService, ordinalRepository, enabledProperties(), clock);

        service.recordRequest("1.1.1.1", UUID.fromString("11111111-1111-1111-1111-111111111111"));

        verify(analyticsService).recordUv(LocalDate.of(2026, 4, 26), "1.1.1.1");
        verify(analyticsService).recordDau(LocalDate.of(2026, 4, 26), 9);
    }

    @Test
    void shouldFailOpenWhenAnalyticsWriteThrows() {
        AnalyticsService analyticsService = mock(AnalyticsService.class);
        AnalyticsUserOrdinalRepository ordinalRepository = mock(AnalyticsUserOrdinalRepository.class);
        doThrow(new RuntimeException("redis down")).when(analyticsService).recordUv(LocalDate.of(2026, 4, 26), "1.1.1.1");
        AnalyticsIngestService service = new AnalyticsIngestService(analyticsService, ordinalRepository, enabledProperties(), clock);

        service.recordRequest("1.1.1.1", null);

        verifyNoInteractions(ordinalRepository);
    }

    @Test
    void shouldSkipWhenDisabled() {
        AnalyticsService analyticsService = mock(AnalyticsService.class);
        AnalyticsUserOrdinalRepository ordinalRepository = mock(AnalyticsUserOrdinalRepository.class);
        AnalyticsIngestProperties properties = new AnalyticsIngestProperties();
        properties.setEnabled(false);
        AnalyticsIngestService service = new AnalyticsIngestService(analyticsService, ordinalRepository, properties, clock);

        service.recordRequest("1.1.1.1", UUID.fromString("11111111-1111-1111-1111-111111111111"));
        service.recordLoginSuccess(UUID.fromString("11111111-1111-1111-1111-111111111111"));

        verifyNoInteractions(analyticsService, ordinalRepository);
    }

    @Test
    void shouldRespectUvAndDauRecordFlags() {
        AnalyticsService analyticsService = mock(AnalyticsService.class);
        AnalyticsUserOrdinalRepository ordinalRepository = mock(AnalyticsUserOrdinalRepository.class);
        AnalyticsIngestProperties properties = enabledProperties();
        properties.setRecordUv(false);
        properties.setRecordDau(false);
        AnalyticsIngestService service = new AnalyticsIngestService(analyticsService, ordinalRepository, properties, clock);

        service.recordRequest("1.1.1.1", UUID.fromString("11111111-1111-1111-1111-111111111111"));
        service.recordLoginSuccess(UUID.fromString("11111111-1111-1111-1111-111111111111"));

        verifyNoInteractions(analyticsService, ordinalRepository);
    }

    private AnalyticsIngestProperties enabledProperties() {
        AnalyticsIngestProperties properties = new AnalyticsIngestProperties();
        properties.setEnabled(true);
        return properties;
    }
}
