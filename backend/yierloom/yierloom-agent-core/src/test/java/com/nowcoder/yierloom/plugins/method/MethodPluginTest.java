package com.nowcoder.yierloom.plugins.method;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import com.nowcoder.yierloom.api.DiagnosticEvent;
import com.nowcoder.yierloom.api.PluginConfig;
import com.nowcoder.yierloom.api.PluginConfigurationException;
import com.nowcoder.yierloom.api.PluginObservation;
import com.nowcoder.yierloom.api.YierLoomBridge;
import com.nowcoder.yierloom.plugins.PluginTestContext;
import com.nowcoder.yierloom.testkit.PluginContractVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MethodPluginTest {

    @Test
    void declaresStableDefaultEnabledDescriptorAndIndependentInstrumentation() {
        MethodPlugin plugin = new MethodPlugin();

        assertThat(plugin.descriptor().id()).isEqualTo("method");
        assertThat(plugin.descriptor().name()).isEqualTo("Method Diagnostics");
        assertThat(plugin.descriptor().version()).isEqualTo("1.0.0");
        assertThat(plugin.descriptor().apiVersion()).isEqualTo("1.0.0");
        assertThat(plugin.descriptor().defaultEnabled()).isTrue();
        assertThat(plugin.descriptor().order()).isEqualTo(100);
        assertThat(plugin.instrumentations(PluginConfig.empty()))
                .singleElement()
                .extracting(module -> module.id())
                .isEqualTo("method");

        MethodPlugin.MethodSettings settings = MethodPlugin.MethodSettings.from(PluginConfig.empty());
        assertThat(settings.sampleRate()).isEqualTo(1.0);
        assertThat(settings.maxEventsPerSecond()).isEqualTo(20);
        assertThat(settings.summaryInterval()).isEqualTo(java.time.Duration.ofSeconds(60));
        assertThat(settings.topN()).isEqualTo(50);
        assertThat(settings.maxTrackedKeys()).isEqualTo(10_000);
        assertThat(settings.slowThresholdMs()).isEqualTo(100);
    }

    @Test
    void aggregatesCumulativelyAndRateLimitsOnlySlowEvents() throws Exception {
        PluginTestContext context = new PluginTestContext(Map.of(
                "slow-threshold", "10ms",
                "summary-interval", "1s",
                "sample-rate", "1.0",
                "max-events-per-second", "1"));
        MethodPlugin plugin = new MethodPlugin();
        plugin.start(context);

        context.deliver(methodObservation(25, "trace-a", "span-a"));
        context.deliver(methodObservation(35, "trace-b", "span-b"));
        context.runTask("method-summary");

        DiagnosticEvent slow = context.singleEvent("method_slow_call");
        assertThat(slow.attributes()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "event.outcome", "threshold",
                "method.class", "com.example.Service",
                "method.name", "work",
                "method.signature.hash", "47f232ef0b4f7114",
                "trace.id", "trace-a",
                "span.id", "span-a"));
        assertThat(slow.longFields()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "duration.ms", 25L,
                "threshold.ms", 10L));
        assertThat(slow.attributes()).doesNotContainKeys(
                "method.descriptor", "message", "exception.stacktrace");

        DiagnosticEvent summary = context.singleEvent("method_latency_summary");
        assertThat(summary.attributes()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "event.outcome", "success",
                "method.class", "com.example.Service",
                "method.name", "work",
                "method.signature.hash", "47f232ef0b4f7114"));
        assertThat(summary.longFields()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "method.invocation.count", 2L,
                "duration.avg.ms", 30L,
                "duration.max.ms", 35L,
                "duration.p95.ms", 50L));

        context.runTask("method-summary");
        assertThat(context.events("method_latency_summary")).hasSize(2);
        assertThat(context.events("method_latency_summary").get(1).longFields())
                .containsEntry("method.invocation.count", 2L);
        plugin.stop();
    }

    @Test
    void samplingZeroUnknownTypesAndStopAllRemainSilent() throws Exception {
        PluginTestContext context = new PluginTestContext(Map.of(
                "sample-rate", "0",
                "summary-interval", "1s"));
        MethodPlugin plugin = new MethodPlugin();
        plugin.start(context);

        context.deliver(PluginObservation.builder("testkit-contract-probe").build());
        context.deliver(methodObservation(200, null, null));
        context.runTask("method-summary");
        plugin.stop();
        plugin.stop();
        context.deliver(methodObservation(200, null, null));

        assertThat(context.emittedEvents()).isEmpty();
    }

    @Test
    void helperSuppressesReentrantObservation() {
        AtomicInteger observations = new AtomicInteger();
        YierLoomBridge.Endpoint endpoint = new YierLoomBridge.Endpoint() {
            @Override
            public boolean observe(String pluginId, PluginObservation observation) {
                observations.incrementAndGet();
                MethodObservationHelper.observe("nested.Service", "nested", "()V", 1);
                return true;
            }

            @Override
            public boolean emit(String pluginId, DiagnosticEvent event) {
                return false;
            }
        };
        assertThat(YierLoomBridge.install(endpoint)).isTrue();
        try {
            MethodObservationHelper.observe("com.example.Service", "work", "()V", 1);
        } finally {
            assertThat(YierLoomBridge.clear(endpoint)).isTrue();
        }

        assertThat(observations).hasValue(1);
    }

    @Test
    void helperIgnoresContextClassLoaderLookupFailures() {
        AtomicReference<PluginObservation> captured = new AtomicReference<>();
        YierLoomBridge.Endpoint endpoint = new YierLoomBridge.Endpoint() {
            @Override
            public boolean observe(String pluginId, PluginObservation observation) {
                captured.set(observation);
                return true;
            }

            @Override
            public boolean emit(String pluginId, DiagnosticEvent event) {
                return false;
            }
        };
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        ClassLoader failing = new ClassLoader(null) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) {
                throw new IllegalStateException("class lookup failed");
            }
        };
        assertThat(YierLoomBridge.install(endpoint)).isTrue();
        try {
            Thread.currentThread().setContextClassLoader(failing);
            MethodObservationHelper.observe("com.example.Service", "work", "()V", 1);
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
            assertThat(YierLoomBridge.clear(endpoint)).isTrue();
        }

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().attributes()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "method.class", "com.example.Service",
                "method.name", "work",
                "method.descriptor", "()V"));
    }

    @ParameterizedTest
    @MethodSource("invalidSettings")
    void rejectsExplicitInvalidSettingsFromInstrumentationAndRuntime(
            String key,
            String value
    ) {
        MethodPlugin plugin = new MethodPlugin();
        PluginConfig config = PluginConfig.of(Map.of(key, value));

        assertThatThrownBy(() -> plugin.instrumentations(config))
                .isInstanceOf(PluginConfigurationException.class)
                .hasMessageContaining(key)
                .hasMessageNotContaining(value);
        assertThatThrownBy(() -> plugin.start(new PluginTestContext(Map.of(key, value))))
                .isInstanceOf(PluginConfigurationException.class)
                .hasMessageContaining(key)
                .hasMessageNotContaining(value);
    }

    @Test
    void rejectsUnknownKeysDeterministically() {
        MethodPlugin plugin = new MethodPlugin();

        assertThatThrownBy(() -> plugin.instrumentations(PluginConfig.of(Map.of(
                "zzz-private", "secret-z",
                "aaa-private", "secret-a"))))
                .isInstanceOfSatisfying(
                        PluginConfigurationException.class,
                        failure -> assertThat(failure.key()).isEqualTo("aaa-private"))
                .hasMessageNotContaining("secret-a")
                .hasMessageNotContaining("secret-z");
    }

    @Test
    void passesTheBuiltInPluginContractVerifier() {
        assertThat(PluginContractVerifier.verifyProvider(
                new MethodPlugin(),
                PluginConfig.of(Map.of(
                        "includes", "com.example.*",
                        "summary-interval", "1s"))).valid()).isTrue();
    }

    private static Stream<Arguments> invalidSettings() {
        return Stream.of(
                Arguments.of("sample-rate", "1.2"),
                Arguments.of("sample-rate", "NaN"),
                Arguments.of("max-events-per-second", "-1"),
                Arguments.of("summary-interval", "0s"),
                Arguments.of("top-n", "0"),
                Arguments.of("max-tracked-keys", "0"),
                Arguments.of("slow-threshold", "-1ms"));
    }

    private static PluginObservation methodObservation(
            long durationMs,
            String traceId,
            String spanId
    ) {
        PluginObservation.Builder observation = PluginObservation.builder("method-call")
                .attribute("method.class", "com.example.Service")
                .attribute("method.name", "work")
                .attribute("method.descriptor", "()V")
                .attribute("message", "must-not-leak")
                .attribute("exception.stacktrace", "must-not-leak")
                .longField("duration.ms", durationMs);
        if (traceId != null) {
            observation.attribute("trace.id", traceId);
        }
        if (spanId != null) {
            observation.attribute("span.id", spanId);
        }
        return observation.build();
    }
}
