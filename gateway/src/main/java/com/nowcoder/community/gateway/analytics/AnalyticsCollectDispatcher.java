package com.nowcoder.community.gateway.analytics;

import com.nowcoder.community.gateway.config.AnalyticsCollectProperties;
import com.nowcoder.community.gateway.filter.TraceIdSupport;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.TimeoutException;

/**
 * 网关 analytics 采集异步调度器：
 * - filter 内仅投递事件（避免 per-request subscribe 触发远程调用副作用）
 * - worker 统一执行 WebClient 调用（有界队列 + 并发上限 + 超时）
 *
 * <p>采集属于“可丢弃”链路：主请求转发永远优先。</p>
 */
@Component
public class AnalyticsCollectDispatcher {

    private static final String HEADER_INTERNAL_TOKEN = "X-Internal-Token";

    private static final String METRIC_TOTAL = "gateway_analytics_collect_total";
    private static final String METRIC_LATENCY = "gateway_analytics_collect_latency";

    private final AnalyticsCollectProperties properties;
    private final WebClient webClient;
    private final MeterRegistry meterRegistry;
    private final Sinks.Many<Task> sink;

    public AnalyticsCollectDispatcher(
            AnalyticsCollectProperties properties,
            WebClient.Builder webClientBuilder,
            MeterRegistry meterRegistry
    ) {
        this.properties = properties;
        this.webClient = webClientBuilder.build();
        this.meterRegistry = meterRegistry;

        int capacity = Math.max(256, properties == null ? 10_000 : properties.getQueueCapacity());
        this.sink = Sinks.many().multicast().onBackpressureBuffer(capacity, false);
    }

    @PostConstruct
    public void start() {
        if (!isEnabled()) {
            return;
        }
        int concurrency = Math.max(1, properties.getMaxConcurrency());
        sink.asFlux()
                .flatMap(task -> Mono.defer(() -> submit(task))
                        .onErrorResume(ex -> {
                            count(task == null ? "unknown" : task.metric(), "worker_error");
                            return Mono.empty();
                        }), concurrency)
                .subscribe();
    }

    public void trySubmitUv(String ip, LocalDate date, String traceId, String traceparent) {
        if (!isEnabled()) {
            return;
        }
        if (!StringUtils.hasText(ip) || date == null) {
            return;
        }
        emit(new Task("uv", ip.trim(), null, date, traceId, traceparent));
    }

    public void trySubmitDau(int userId, LocalDate date, String traceId, String traceparent) {
        if (!isEnabled()) {
            return;
        }
        if (userId <= 0 || date == null) {
            return;
        }
        emit(new Task("dau", null, userId, date, traceId, traceparent));
    }

    private boolean isEnabled() {
        return properties != null
                && properties.isEnabled()
                && StringUtils.hasText(properties.getInternalToken());
    }

    private void emit(Task task) {
        Sinks.EmitResult r = sink.tryEmitNext(task);
        if (r.isSuccess()) {
            count(task.metric(), "queued");
            return;
        }
        // 有界队列满：允许丢弃（采集链路不影响主链路）
        count(task.metric(), "dropped");
    }

    private Mono<Void> submit(Task task) {
        if (task == null) {
            return Mono.empty();
        }
        String metric = task.metric();
        long startNanos = System.nanoTime();

        Mono<Void> call = buildCall(task);
        if (call == null) {
            return Mono.empty();
        }

        count(metric, "attempt");

        long timeoutMs = Math.max(50, properties.getTimeoutMs());
        return call.timeout(Duration.ofMillis(timeoutMs))
                .doOnSuccess(v -> count(metric, "ok"))
                .doOnError(ex -> count(metric, outcomeOf(ex)))
                .doFinally(sig -> recordLatency(metric, startNanos))
                .onErrorResume(e -> Mono.empty());
    }

    private Mono<Void> buildCall(Task task) {
        if (task == null) {
            return null;
        }
        String metric = task.metric();
        if (!StringUtils.hasText(metric) || !StringUtils.hasText(properties.getInternalToken())) {
            return null;
        }

        String traceId = TraceIdSupport.normalizeTraceId(task.traceId());
        String traceparent = StringUtils.hasText(task.traceparent())
                ? task.traceparent().trim()
                : (StringUtils.hasText(traceId) ? TraceIdSupport.buildTraceparent(traceId) : null);

        if ("uv".equals(metric)) {
            String ip = task.ip();
            if (!StringUtils.hasText(ip) || task.date() == null) {
                return null;
            }
            return webClient.post()
                    .uri("lb://analytics-service/internal/analytics/uv/record")
                    .header(HEADER_INTERNAL_TOKEN, properties.getInternalToken())
                    .headers(h -> {
                        if (StringUtils.hasText(traceId)) {
                            h.set(TraceIdSupport.HEADER_TRACE_ID, traceId);
                        }
                        if (StringUtils.hasText(traceparent)) {
                            h.set(TraceIdSupport.HEADER_TRACEPARENT, traceparent);
                        }
                    })
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData("ip", ip).with("date", task.date().toString()))
                    .retrieve()
                    .bodyToMono(Void.class);
        }

        if ("dau".equals(metric)) {
            Integer userId = task.userId();
            if (userId == null || userId <= 0 || task.date() == null) {
                return null;
            }
            return webClient.post()
                    .uri("lb://analytics-service/internal/analytics/dau/record")
                    .header(HEADER_INTERNAL_TOKEN, properties.getInternalToken())
                    .headers(h -> {
                        if (StringUtils.hasText(traceId)) {
                            h.set(TraceIdSupport.HEADER_TRACE_ID, traceId);
                        }
                        if (StringUtils.hasText(traceparent)) {
                            h.set(TraceIdSupport.HEADER_TRACEPARENT, traceparent);
                        }
                    })
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData("userId", String.valueOf(userId)).with("date", task.date().toString()))
                    .retrieve()
                    .bodyToMono(Void.class);
        }

        return null;
    }

    private void recordLatency(String metric, long startNanos) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.timer(METRIC_LATENCY, Tags.of("metric", metric)).record(
                System.nanoTime() - startNanos,
                java.util.concurrent.TimeUnit.NANOSECONDS
        );
    }

    private void count(String metric, String outcome) {
        if (meterRegistry == null) {
            return;
        }
        String m = StringUtils.hasText(metric) ? metric.trim().toLowerCase() : "unknown";
        String o = StringUtils.hasText(outcome) ? outcome.trim().toLowerCase() : "unknown";
        meterRegistry.counter(METRIC_TOTAL, Tags.of("metric", m, "outcome", o)).increment();
    }

    private String outcomeOf(Throwable ex) {
        if (ex instanceof TimeoutException) {
            return "timeout";
        }
        return "error";
    }

    private record Task(String metric, String ip, Integer userId, LocalDate date, String traceId, String traceparent) {
    }
}
