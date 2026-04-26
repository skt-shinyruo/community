package com.nowcoder.community.analytics.ingest;

import com.nowcoder.community.analytics.repo.AnalyticsUserOrdinalRepository;
import com.nowcoder.community.analytics.service.AnalyticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class AnalyticsIngestService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsIngestService.class);

    private final AnalyticsService analyticsService;
    private final AnalyticsUserOrdinalRepository ordinalRepository;
    private final AnalyticsIngestProperties properties;
    private final Clock clock;

    public AnalyticsIngestService(
            AnalyticsService analyticsService,
            AnalyticsUserOrdinalRepository ordinalRepository,
            AnalyticsIngestProperties properties
    ) {
        this(analyticsService, ordinalRepository, properties, Clock.systemDefaultZone());
    }

    AnalyticsIngestService(
            AnalyticsService analyticsService,
            AnalyticsUserOrdinalRepository ordinalRepository,
            AnalyticsIngestProperties properties,
            Clock clock
    ) {
        this.analyticsService = analyticsService;
        this.ordinalRepository = ordinalRepository;
        this.properties = properties;
        this.clock = clock;
    }

    public void recordRequest(String ip, UUID userId) {
        if (properties == null || !properties.isEnabled()) {
            return;
        }
        LocalDate today = LocalDate.now(clock);
        if (properties.isRecordUv()) {
            recordUv(today, ip);
        }
        if (properties.isRecordDau()) {
            recordDau(today, userId);
        }
    }

    public void recordLoginSuccess(UUID userId) {
        if (properties == null || !properties.isEnabled() || !properties.isRecordDau()) {
            return;
        }
        recordDau(LocalDate.now(clock), userId);
    }

    private void recordUv(LocalDate date, String ip) {
        if (!StringUtils.hasText(ip)) {
            return;
        }
        try {
            analyticsService.recordUv(date, ip);
        } catch (RuntimeException e) {
            log.warn("[analytics][ingest] record UV failed: date={}, ip={}", date, ip, e);
        }
    }

    private void recordDau(LocalDate date, UUID userId) {
        if (userId == null) {
            return;
        }
        try {
            int ordinal = ordinalRepository.resolveOrdinal(userId);
            analyticsService.recordDau(date, ordinal);
        } catch (RuntimeException e) {
            log.warn("[analytics][ingest] record DAU failed: date={}, userId={}", date, userId, e);
        }
    }
}
