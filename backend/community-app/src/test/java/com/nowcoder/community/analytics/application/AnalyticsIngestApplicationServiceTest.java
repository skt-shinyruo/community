package com.nowcoder.community.analytics.application;

import com.nowcoder.community.analytics.application.command.RecordRequestCommand;
import com.nowcoder.community.analytics.config.AnalyticsIngestProperties;
import com.nowcoder.community.analytics.domain.repository.AnalyticsRepository;
import com.nowcoder.community.analytics.domain.repository.AnalyticsUserOrdinalRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AnalyticsIngestApplicationServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-04-26T01:02:03Z"), ZoneOffset.UTC);

    @Test
    void shouldRecordUvAndDauForRequest() {
        AnalyticsRepository analyticsRepository = mock(AnalyticsRepository.class);
        AnalyticsUserOrdinalRepository ordinalRepository = mock(AnalyticsUserOrdinalRepository.class);
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(ordinalRepository.ordinalOf(userId)).thenReturn(9);
        AnalyticsIngestApplicationService service = newService(analyticsRepository, ordinalRepository);

        service.recordRequest(new RecordRequestCommand("1.1.1.1", userId, true, true));

        verify(analyticsRepository).recordUv(LocalDate.of(2026, 4, 26), "1.1.1.1");
        verify(analyticsRepository).recordDau(LocalDate.of(2026, 4, 26), 9);
    }

    @Test
    void shouldFailOpenWhenAnalyticsWriteThrows() {
        AnalyticsRepository analyticsRepository = mock(AnalyticsRepository.class);
        AnalyticsUserOrdinalRepository ordinalRepository = mock(AnalyticsUserOrdinalRepository.class);
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(ordinalRepository.ordinalOf(userId)).thenReturn(9);
        doThrow(new RuntimeException("redis down"))
                .when(analyticsRepository).recordUv(LocalDate.of(2026, 4, 26), "1.1.1.1");
        AnalyticsIngestApplicationService service = newService(analyticsRepository, ordinalRepository);

        service.recordRequest(new RecordRequestCommand("1.1.1.1", userId, true, true));

        verify(analyticsRepository).recordDau(LocalDate.of(2026, 4, 26), 9);
    }

    @Test
    void shouldFailOpenWhenOrdinalResolutionThrows() {
        AnalyticsRepository analyticsRepository = mock(AnalyticsRepository.class);
        AnalyticsUserOrdinalRepository ordinalRepository = mock(AnalyticsUserOrdinalRepository.class);
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        doThrow(new RuntimeException("redis down")).when(ordinalRepository).ordinalOf(userId);
        AnalyticsIngestApplicationService service = newService(analyticsRepository, ordinalRepository);

        service.recordRequest(new RecordRequestCommand("1.1.1.1", userId, true, true));

        verify(analyticsRepository).recordUv(LocalDate.of(2026, 4, 26), "1.1.1.1");
    }

    @Test
    void shouldFailOpenWhenDauWriteThrows() {
        AnalyticsRepository analyticsRepository = mock(AnalyticsRepository.class);
        AnalyticsUserOrdinalRepository ordinalRepository = mock(AnalyticsUserOrdinalRepository.class);
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(ordinalRepository.ordinalOf(userId)).thenReturn(9);
        doThrow(new RuntimeException("redis down"))
                .when(analyticsRepository).recordDau(LocalDate.of(2026, 4, 26), 9);
        AnalyticsIngestApplicationService service = newService(analyticsRepository, ordinalRepository);

        service.recordRequest(new RecordRequestCommand("1.1.1.1", userId, true, true));

        verify(analyticsRepository).recordUv(LocalDate.of(2026, 4, 26), "1.1.1.1");
    }

    @Test
    void shouldRespectUvAndDauRecordFlags() {
        AnalyticsRepository analyticsRepository = mock(AnalyticsRepository.class);
        AnalyticsUserOrdinalRepository ordinalRepository = mock(AnalyticsUserOrdinalRepository.class);
        AnalyticsIngestApplicationService service = newService(analyticsRepository, ordinalRepository);

        service.recordRequest(new RecordRequestCommand(
                "1.1.1.1",
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                false,
                false
        ));

        verifyNoInteractions(analyticsRepository, ordinalRepository);
    }

    @Test
    void shouldIgnoreBlankUvIpAndMissingDauUser() {
        AnalyticsRepository analyticsRepository = mock(AnalyticsRepository.class);
        AnalyticsUserOrdinalRepository ordinalRepository = mock(AnalyticsUserOrdinalRepository.class);
        AnalyticsIngestApplicationService service = newService(analyticsRepository, ordinalRepository);

        service.recordRequest(new RecordRequestCommand(" ", null, true, true));
        service.recordLoginSuccess(null);

        verifyNoInteractions(analyticsRepository, ordinalRepository);
    }

    @Test
    void recordRequestShouldRejectNullCommand() {
        AnalyticsIngestApplicationService service = newService(mock(AnalyticsRepository.class), mock(AnalyticsUserOrdinalRepository.class));

        assertThatThrownBy(() -> service.recordRequest(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void loginSuccessDauShouldFollowIngestPropertiesGate() {
        AnalyticsRepository analyticsRepository = mock(AnalyticsRepository.class);
        AnalyticsUserOrdinalRepository ordinalRepository = mock(AnalyticsUserOrdinalRepository.class);
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(ordinalRepository.ordinalOf(userId)).thenReturn(9);
        AnalyticsIngestApplicationService disabled = newService(
                analyticsRepository, ordinalRepository, ingestProperties(false, true));

        disabled.recordLoginSuccess(userId);

        verifyNoInteractions(analyticsRepository, ordinalRepository);

        AnalyticsIngestApplicationService enabled = newService(
                analyticsRepository, ordinalRepository, ingestProperties(true, true));

        enabled.recordLoginSuccess(userId);

        verify(analyticsRepository).recordDau(LocalDate.of(2026, 4, 26), 9);
    }

    private AnalyticsIngestApplicationService newService(
            AnalyticsRepository analyticsRepository,
            AnalyticsUserOrdinalRepository ordinalRepository
    ) {
        return newService(analyticsRepository, ordinalRepository, ingestProperties(true, true));
    }

    private AnalyticsIngestApplicationService newService(
            AnalyticsRepository analyticsRepository,
            AnalyticsUserOrdinalRepository ordinalRepository,
            AnalyticsIngestProperties properties
    ) {
        return new AnalyticsIngestApplicationService(
                analyticsRepository,
                ordinalRepository,
                properties,
                clock
        );
    }

    private AnalyticsIngestProperties ingestProperties(boolean enabled, boolean recordDau) {
        AnalyticsIngestProperties properties = new AnalyticsIngestProperties();
        properties.setEnabled(enabled);
        properties.setRecordDau(recordDau);
        return properties;
    }
}
