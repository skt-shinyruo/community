package com.nowcoder.community.gateway.analytics;

import com.nowcoder.community.analytics.api.rpc.InternalAnalyticsRpcService;
import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.gateway.config.AnalyticsCollectProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsCollectDispatcherTest {

    @Test
    void shouldSubmitUvWhenEnabled() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        InternalAnalyticsRpcService rpc = new InternalAnalyticsRpcService() {
            @Override
            public Result<Void> recordUv(String ip, LocalDate date) {
                calls.incrementAndGet();
                return Result.ok();
            }

            @Override
            public Result<Void> recordDau(int userId, LocalDate date) {
                calls.incrementAndGet();
                return Result.ok();
            }
        };

        AnalyticsCollectProperties props = new AnalyticsCollectProperties();
        props.setEnabled(true);
        props.setTimeoutMs(1000);
        props.setMaxConcurrency(2);
        props.setQueueCapacity(10_000);

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AnalyticsCollectDispatcher dispatcher = new AnalyticsCollectDispatcher(props, meterRegistry);
        TestSupport.injectRpc(dispatcher, rpc);
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
    void shouldDropWhenQueueOverflows() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(1);
        InternalAnalyticsRpcService rpc = new InternalAnalyticsRpcService() {
            @Override
            public Result<Void> recordUv(String ip, LocalDate date) {
                calls.incrementAndGet();
                try {
                    latch.await(3, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return Result.ok();
            }

            @Override
            public Result<Void> recordDau(int userId, LocalDate date) {
                calls.incrementAndGet();
                return Result.ok();
            }
        };

        AnalyticsCollectProperties props = new AnalyticsCollectProperties();
        props.setEnabled(true);
        props.setTimeoutMs(60_000);
        props.setMaxConcurrency(1);
        props.setQueueCapacity(256);

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AnalyticsCollectDispatcher dispatcher = new AnalyticsCollectDispatcher(props, meterRegistry);
        TestSupport.injectRpc(dispatcher, rpc);
        dispatcher.start();

        LocalDate today = LocalDate.now();
        for (int i = 0; i < 1000; i++) {
            dispatcher.trySubmitUv("1.2.3." + i, today, null, null);
        }

        // 等待至少一次进入 worker
        for (int i = 0; i < 50 && calls.get() == 0; i++) {
            Thread.sleep(10);
        }
        latch.countDown();

        // 至少会有一次实际尝试（进入 worker），剩余会因有界队列/背压被丢弃。
        assertThat(calls.get()).isGreaterThanOrEqualTo(1);
        assertThat(meterRegistry.find("gateway_analytics_collect_total")
                .tags("metric", "uv", "outcome", "dropped")
                .counter()
                .count()).isGreaterThan(0.0);
    }

    private static final class TestSupport {
        private static void injectRpc(AnalyticsCollectDispatcher dispatcher, InternalAnalyticsRpcService rpc) {
            try {
                var f = AnalyticsCollectDispatcher.class.getDeclaredField("internalAnalyticsRpcService");
                f.setAccessible(true);
                f.set(dispatcher, rpc);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
