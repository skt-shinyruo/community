package com.nowcoder.community.analytics.application;

import com.nowcoder.community.analytics.application.command.RecordLoginSuccessCommand;
import com.nowcoder.community.analytics.application.command.RecordRequestCommand;
import com.nowcoder.community.analytics.domain.model.AnalyticsRequestEvent;
import com.nowcoder.community.analytics.domain.repository.AnalyticsRepository;
import com.nowcoder.community.analytics.domain.repository.AnalyticsUserOrdinalRepository;
import com.nowcoder.community.analytics.domain.service.AnalyticsIngestDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class AnalyticsIngestApplicationService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsIngestApplicationService.class);

    private final AnalyticsRepository analyticsRepository;
    private final AnalyticsUserOrdinalRepository ordinalRepository;
    private final AnalyticsIngestDomainService analyticsIngestDomainService;
    private final Clock clock;
    private final AtomicLong uvFailureCount = new AtomicLong();
    private final AtomicLong dauFailureCount = new AtomicLong();

    @Autowired
    public AnalyticsIngestApplicationService(
            AnalyticsRepository analyticsRepository,
            AnalyticsUserOrdinalRepository ordinalRepository,
            AnalyticsIngestDomainService analyticsIngestDomainService
    ) {
        this(analyticsRepository, ordinalRepository, analyticsIngestDomainService, Clock.systemDefaultZone());
    }

    AnalyticsIngestApplicationService(
            AnalyticsRepository analyticsRepository,
            AnalyticsUserOrdinalRepository ordinalRepository,
            AnalyticsIngestDomainService analyticsIngestDomainService,
            Clock clock
    ) {
        this.analyticsRepository = analyticsRepository;
        this.ordinalRepository = ordinalRepository;
        this.analyticsIngestDomainService = analyticsIngestDomainService;
        this.clock = clock;
    }

    public void recordRequest(RecordRequestCommand command) {
        if (command == null) {
            return;
        }
        LocalDate today = LocalDate.now(clock);
        AnalyticsRequestEvent event = new AnalyticsRequestEvent(
                command.ip(),
                command.userId(),
                command.recordUv(),
                command.recordDau()
        );
        if (analyticsIngestDomainService.shouldRecordUv(event)) {
            recordUv(today, event.ip());
        }
        if (analyticsIngestDomainService.shouldRecordDau(event)) {
            recordDau(today, event.userId());
        }
    }

    public void recordLoginSuccess(RecordLoginSuccessCommand command) {
        if (command == null || !command.recordDau()) {
            return;
        }
        AnalyticsRequestEvent event = new AnalyticsRequestEvent(null, command.userId(), false, true);
        if (analyticsIngestDomainService.shouldRecordDau(event)) {
            recordDau(LocalDate.now(clock), event.userId());
        }
    }

    private void recordUv(LocalDate date, String ip) {
        try {
            analyticsRepository.recordUv(date, ip);
        } catch (RuntimeException e) {
            logFailure("UV", date, uvFailureCount, e);
        }
    }

    private void recordDau(LocalDate date, UUID userId) {
        if (userId == null) {
            return;
        }
        try {
            int ordinal = ordinalRepository.ordinalOf(userId);
            analyticsRepository.recordDau(date, ordinal);
        } catch (RuntimeException e) {
            logFailure("DAU", date, dauFailureCount, e);
        }
    }

    private void logFailure(String metric, LocalDate date, AtomicLong failureCount, RuntimeException e) {
        long count = failureCount.incrementAndGet();
        if (count <= 3 || isPowerOfTwo(count)) {
            log.warn("[analytics][ingest] record {} failed: date={}, failures={}, error={}", metric, date, count, e.toString());
        }
        if (log.isDebugEnabled()) {
            log.debug("[analytics][ingest] record {} failed: date={}, failures={}", metric, date, count, e);
        }
    }

    private boolean isPowerOfTwo(long value) {
        return value > 0 && (value & (value - 1)) == 0;
    }
}
