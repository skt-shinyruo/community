package com.nowcoder.yierloom.plugins.exception;

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

class ExceptionPluginTest {

    @Test
    void declaresStableDefaultEnabledDescriptorAndIndependentInstrumentation() {
        ExceptionPlugin plugin = new ExceptionPlugin();

        assertThat(plugin.descriptor().id()).isEqualTo("exception");
        assertThat(plugin.descriptor().name()).isEqualTo("Exception Diagnostics");
        assertThat(plugin.descriptor().version()).isEqualTo("1.0.0");
        assertThat(plugin.descriptor().apiVersion()).isEqualTo("1.0.0");
        assertThat(plugin.descriptor().defaultEnabled()).isTrue();
        assertThat(plugin.descriptor().order()).isEqualTo(110);
        assertThat(plugin.instrumentations(PluginConfig.empty()))
                .singleElement()
                .extracting(module -> module.id())
                .isEqualTo("exception");

        ExceptionPlugin.ExceptionSettings settings = ExceptionPlugin.ExceptionSettings.from(
                PluginConfig.empty());
        assertThat(settings.sampleRate()).isEqualTo(1.0);
        assertThat(settings.maxEventsPerSecond()).isEqualTo(20);
        assertThat(settings.maxTrackedKeys()).isEqualTo(10_000);
        assertThat(settings.matcher().matches("com.example.Service")).isTrue();
    }

    @Test
    void emitsOnlyWhitelistedExceptionAndMethodIdentityFields() throws Exception {
        PluginTestContext context = new PluginTestContext(Map.of(
                "sample-rate", "1.0",
                "max-events-per-second", "20"));
        ExceptionPlugin plugin = new ExceptionPlugin();
        plugin.start(context);

        context.deliver(exceptionObservation(
                "com.example.Service", "work", "()V", "trace-a", "span-a"));

        DiagnosticEvent event = context.singleEvent("exception_observed");
        assertThat(event.attributes()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "event.outcome", "error",
                "exception.type", "java.lang.IllegalStateException",
                "method.class", "com.example.Service",
                "method.name", "work",
                "method.signature.hash", "47f232ef0b4f7114",
                "trace.id", "trace-a",
                "span.id", "span-a"));
        assertThat(event.attributes()).doesNotContainKeys(
                "method.descriptor",
                "exception.message",
                "exception.stacktrace",
                "message",
                "event.category");
        assertThat(event.toString()).doesNotContain("secret-message", "secret-stack");
        plugin.stop();
    }

    @Test
    void enforcesRateAndKeyBoundsWithoutBlockingAnExistingIdentity() throws Exception {
        PluginTestContext context = new PluginTestContext(Map.of(
                "sample-rate", "1.0",
                "max-events-per-second", "20",
                "max-tracked-keys", "1"));
        ExceptionPlugin plugin = new ExceptionPlugin();
        plugin.start(context);

        context.deliver(exceptionObservation(
                "com.example.Accepted", "work", "()V", null, null));
        context.deliver(exceptionObservation(
                "com.example.Rejected", "work", "()V", null, null));
        context.deliver(exceptionObservation(
                "com.example.Accepted", "work", "()V", null, null));

        assertThat(context.events("exception_observed"))
                .hasSize(2)
                .allSatisfy(event -> assertThat(event.attributes())
                        .containsEntry("method.class", "com.example.Accepted"));
        plugin.stop();
    }

    @Test
    void zeroSamplingZeroRateUnknownTypesAndStopAreSilent() throws Exception {
        PluginTestContext sampledOut = new PluginTestContext(Map.of("sample-rate", "0"));
        ExceptionPlugin samplePlugin = new ExceptionPlugin();
        samplePlugin.start(sampledOut);
        sampledOut.deliver(exceptionObservation(
                "com.example.Service", "work", "()V", null, null));
        sampledOut.deliver(PluginObservation.builder("testkit-contract-probe").build());
        samplePlugin.stop();
        samplePlugin.stop();
        sampledOut.deliver(exceptionObservation(
                "com.example.Service", "work", "()V", null, null));
        assertThat(sampledOut.emittedEvents()).isEmpty();

        PluginTestContext rateLimited = new PluginTestContext(Map.of(
                "sample-rate", "1.0", "max-events-per-second", "0"));
        ExceptionPlugin ratePlugin = new ExceptionPlugin();
        ratePlugin.start(rateLimited);
        rateLimited.deliver(exceptionObservation(
                "com.example.Service", "work", "()V", null, null));
        assertThat(rateLimited.emittedEvents()).isEmpty();
        ratePlugin.stop();
    }

    @Test
    void helperNeverReadsThrowableMessageOrStringRepresentation() {
        AtomicReference<PluginObservation> captured = new AtomicReference<>();
        AtomicInteger observations = new AtomicInteger();
        YierLoomBridge.Endpoint endpoint = new YierLoomBridge.Endpoint() {
            @Override
            public boolean observe(String pluginId, PluginObservation observation) {
                if ("exception".equals(pluginId)) {
                    observations.incrementAndGet();
                    captured.set(observation);
                    ExceptionObservationHelper.observe(
                            "nested.Service", "nested", "()V", new ExplosiveThrowable());
                    return true;
                }
                return false;
            }

            @Override
            public boolean emit(String pluginId, DiagnosticEvent event) {
                return false;
            }
        };
        assertThat(YierLoomBridge.install(endpoint)).isTrue();
        try {
            ExceptionObservationHelper.observe(
                    "com.example.Service", "work", "()V", new ExplosiveThrowable());
        } finally {
            assertThat(YierLoomBridge.clear(endpoint)).isTrue();
        }

        assertThat(captured.get()).isNotNull();
        assertThat(observations).hasValue(1);
        assertThat(captured.get().attributes())
                .containsEntry("exception.type", ExplosiveThrowable.class.getName())
                .doesNotContainKeys("exception.message", "exception.stacktrace");
    }

    @ParameterizedTest
    @MethodSource("invalidSettings")
    void rejectsExplicitInvalidSettingsFromInstrumentationAndRuntime(
            String key,
            String value
    ) {
        ExceptionPlugin plugin = new ExceptionPlugin();
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
    void passesTheBuiltInPluginContractVerifier() {
        assertThat(PluginContractVerifier.verifyProvider(
                new ExceptionPlugin(),
                PluginConfig.of(Map.of("includes", "com.example.*"))).valid()).isTrue();
    }

    private static Stream<Arguments> invalidSettings() {
        return Stream.of(
                Arguments.of("sample-rate", "-0.1"),
                Arguments.of("sample-rate", "Infinity"),
                Arguments.of("max-events-per-second", "-1"),
                Arguments.of("max-tracked-keys", "0"));
    }

    private static PluginObservation exceptionObservation(
            String className,
            String methodName,
            String descriptor,
            String traceId,
            String spanId
    ) {
        PluginObservation.Builder observation = PluginObservation.builder("exception-thrown")
                .attribute("method.class", className)
                .attribute("method.name", methodName)
                .attribute("method.descriptor", descriptor)
                .attribute("exception.type", "java.lang.IllegalStateException")
                .attribute("exception.message", "secret-message")
                .attribute("exception.stacktrace", "secret-stack")
                .attribute("message", "secret-message")
                .attribute("event.category", "forged");
        if (traceId != null) {
            observation.attribute("trace.id", traceId);
        }
        if (spanId != null) {
            observation.attribute("span.id", spanId);
        }
        return observation.build();
    }

    private static final class ExplosiveThrowable extends RuntimeException {
        @Override
        public String getMessage() {
            throw new AssertionError("message was accessed");
        }

        @Override
        public String toString() {
            throw new AssertionError("toString was accessed");
        }
    }
}
