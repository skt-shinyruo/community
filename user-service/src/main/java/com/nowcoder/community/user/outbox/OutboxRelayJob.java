package com.nowcoder.community.user.outbox;

// Outbox Relay 任务：批量拉取待投递事件并发送到 Kafka。
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@ConditionalOnProperty(name = "user.events.outbox.enabled", havingValue = "true")
public class OutboxRelayJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayJob.class);

    private final OutboxEventService outboxEventService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final UserOutboxProperties properties;
    private final MeterRegistry meterRegistry;

    // 用于 Prometheus/Grafana 告警的 backlog 指标（避免每次 scrape 都打 DB）。
    private final AtomicInteger newCountGauge = new AtomicInteger();
    private final AtomicInteger retryCountGauge = new AtomicInteger();
    private final AtomicInteger sendingCountGauge = new AtomicInteger();
    private final AtomicInteger failedCountGauge = new AtomicInteger();

    public OutboxRelayJob(
            OutboxEventService outboxEventService,
            KafkaTemplate<String, String> kafkaTemplate,
            UserOutboxProperties properties,
            MeterRegistry meterRegistry
    ) {
        this.outboxEventService = outboxEventService;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.meterRegistry = meterRegistry;

        if (meterRegistry != null) {
            meterRegistry.gauge("user_outbox_backlog", Tags.of("status", "NEW"), newCountGauge);
            meterRegistry.gauge("user_outbox_backlog", Tags.of("status", "RETRY"), retryCountGauge);
            meterRegistry.gauge("user_outbox_backlog", Tags.of("status", "SENDING"), sendingCountGauge);
            meterRegistry.gauge("user_outbox_backlog", Tags.of("status", "FAILED"), failedCountGauge);
        }
    }

    @Scheduled(fixedDelayString = "${user.events.outbox.relay-interval-ms:5000}")
    public void relay() {
        if (!properties.isEnabled() || !properties.isRelayEnabled()) {
            return;
        }
        refreshBacklogMetrics();
        List<OutboxEvent> events = outboxEventService.claimBatch(properties.getBatchSize());
        if (events == null || events.isEmpty()) {
            return;
        }
        for (OutboxEvent event : events) {
            if (event == null || event.getId() == null) {
                continue;
            }
            deliver(event);
        }
    }

    private void deliver(OutboxEvent event) {
        long id = event.getId();
        try {
            CompletableFuture<?> future = kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayload());
            future.get(properties.getSendTimeoutMs(), TimeUnit.MILLISECONDS);
            outboxEventService.markSent(id);
            if (meterRegistry != null) {
                meterRegistry.counter("user_outbox_deliver_total", Tags.of("topic", event.getTopic(), "outcome", "sent")).increment();
            }
        } catch (Exception ex) {
            int retryCount = event.getRetryCount() == null ? 0 : event.getRetryCount();
            retryCount++;
            if (retryCount > properties.getMaxRetries()) {
                outboxEventService.markFailed(id, safeError(ex));
                if (meterRegistry != null) {
                    meterRegistry.counter("user_outbox_deliver_total", Tags.of("topic", event.getTopic(), "outcome", "failed")).increment();
                }
                log.warn("[outbox] send failed, marked failed (id={}, topic={}): {}", id, event.getTopic(), ex.toString());
                return;
            }
            long delay = nextDelayMs(retryCount);
            Date nextRetryAt = Date.from(Instant.now().plusMillis(delay));
            outboxEventService.markRetry(id, retryCount, nextRetryAt, safeError(ex));
            if (meterRegistry != null) {
                meterRegistry.counter("user_outbox_deliver_total", Tags.of("topic", event.getTopic(), "outcome", "retry")).increment();
            }
            log.warn("[outbox] send failed, retry scheduled (id={}, retry={}, delayMs={}): {}", id, retryCount, delay, ex.toString());
        }
    }

    private void refreshBacklogMetrics() {
        try {
            newCountGauge.set(outboxEventService.countByStatus("NEW"));
            retryCountGauge.set(outboxEventService.countByStatus("RETRY"));
            sendingCountGauge.set(outboxEventService.countByStatus("SENDING"));
            failedCountGauge.set(outboxEventService.countByStatus("FAILED"));
        } catch (Exception ignored) {
            // metrics 不应影响主链路
        }
    }

    private long nextDelayMs(int retryCount) {
        long base = Math.max(100, properties.getBaseDelayMs());
        long max = Math.max(base, properties.getMaxDelayMs());
        int exp = Math.min(10, Math.max(1, retryCount));
        long delay = base * (1L << exp);
        return Math.min(delay, max);
    }

    private String safeError(Exception ex) {
        String msg = ex == null ? "" : ex.getMessage();
        if (msg == null) {
            msg = "";
        }
        return msg.length() > 200 ? msg.substring(0, 200) : msg;
    }
}

