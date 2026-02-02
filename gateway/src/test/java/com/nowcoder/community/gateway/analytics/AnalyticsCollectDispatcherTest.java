package com.nowcoder.community.gateway.analytics;

import com.nowcoder.community.gateway.config.AnalyticsCollectProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsCollectDispatcherTest {

    @Test
    void shouldSubmitUvWhenEnabled() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        WebClient.Builder webClientBuilder = WebClient.builder()
                .exchangeFunction(request -> {
                    calls.incrementAndGet();
                    return Mono.just(ClientResponse.create(HttpStatus.OK).build());
                });

        AnalyticsCollectProperties props = new AnalyticsCollectProperties();
        props.setEnabled(true);
        props.setInternalToken("t");
        props.setTimeoutMs(1000);
        props.setMaxConcurrency(2);
        props.setQueueCapacity(10_000);

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AnalyticsCollectDispatcher dispatcher = new AnalyticsCollectDispatcher(props, webClientBuilder, meterRegistry);
        dispatcher.start();

        dispatcher.trySubmitUv("1.2.3.4", LocalDate.now(), "trace-1", null);

        // 异步 worker：等待最多 500ms
        for (int i = 0; i < 50 && calls.get() == 0; i++) {
            Thread.sleep(10);
        }

        assertThat(calls.get()).isGreaterThanOrEqualTo(1);
        assertThat(meterRegistry.find("gateway_analytics_collect_total")
                .tags("metric", "uv", "outcome", "queued")
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    void shouldDropWhenQueueOverflows() {
        AtomicInteger calls = new AtomicInteger();
        WebClient.Builder webClientBuilder = WebClient.builder()
                .exchangeFunction(request -> {
                    calls.incrementAndGet();
                    return Mono.never();
                });

        AnalyticsCollectProperties props = new AnalyticsCollectProperties();
        props.setEnabled(true);
        props.setInternalToken("t");
        props.setTimeoutMs(60_000);
        props.setMaxConcurrency(1);
        props.setQueueCapacity(256);

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AnalyticsCollectDispatcher dispatcher = new AnalyticsCollectDispatcher(props, webClientBuilder, meterRegistry);
        dispatcher.start();

        LocalDate today = LocalDate.now();
        for (int i = 0; i < 1000; i++) {
            dispatcher.trySubmitUv("1.2.3." + i, today, null, null);
        }

        // 至少会有一次实际尝试（进入 worker），剩余会因有界队列/背压被丢弃。
        assertThat(calls.get()).isGreaterThanOrEqualTo(1);
        assertThat(meterRegistry.find("gateway_analytics_collect_total")
                .tags("metric", "uv", "outcome", "dropped")
                .counter()
                .count()).isGreaterThan(0.0);
    }
}

