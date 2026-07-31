package com.nowcoder.yierloom.plugins.http;

import java.lang.reflect.Method;
import java.net.URI;
import java.time.Duration;
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
import com.nowcoder.yierloom.sdk.AdviceTransformer;
import com.nowcoder.yierloom.testkit.PluginContractVerifier;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.FixedValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class HttpPluginTest {

    @Test
    void declaresStableDefaultDisabledDescriptorAndSettings() {
        HttpPlugin plugin = new HttpPlugin();

        assertThat(plugin.descriptor().id()).isEqualTo("http");
        assertThat(plugin.descriptor().name()).isEqualTo("HTTP Diagnostics");
        assertThat(plugin.descriptor().version()).isEqualTo("1.0.0");
        assertThat(plugin.descriptor().apiVersion()).isEqualTo("1.0.0");
        assertThat(plugin.descriptor().defaultEnabled()).isFalse();
        assertThat(plugin.descriptor().order()).isEqualTo(200);

        HttpInstrumentationModule module = (HttpInstrumentationModule) plugin
                .instrumentations(PluginConfig.empty())
                .get(0);
        assertThat(module.id()).isEqualTo("http");
        assertThat(module.typeInstrumentations()).singleElement();
        assertThat(module.helperClassNames())
                .containsExactly(HttpObservationHelper.class.getName());

        HttpPlugin.HttpSettings settings = HttpPlugin.HttpSettings.from(PluginConfig.empty());
        assertThat(settings.sampleRate()).isEqualTo(1.0);
        assertThat(settings.maxEventsPerSecond()).isEqualTo(20);
        assertThat(settings.summaryInterval()).isEqualTo(Duration.ofSeconds(60));
        assertThat(settings.topN()).isEqualTo(50);
        assertThat(settings.maxTrackedKeys()).isEqualTo(10_000);
        assertThat(settings.slowThresholdMs()).isEqualTo(500);
    }

    @Test
    void matchesExactExchangeFunctionTypeAndExchangeMethod() {
        DynamicType.Unloaded<?> exchangeFunction = new ByteBuddy()
                .makeInterface()
                .name("org.springframework.web.reactive.function.client.ExchangeFunction")
                .defineMethod("exchange", Object.class, Visibility.PUBLIC)
                .withParameters(Object.class)
                .withoutCode()
                .make();
        DynamicType.Unloaded<?> implementation = new ByteBuddy()
                .subclass(Object.class)
                .name("fixture.HttpExchangeFunction")
                .implement(exchangeFunction.getTypeDescription())
                .defineMethod("exchange", Object.class, Visibility.PUBLIC)
                .withParameters(Object.class)
                .intercept(FixedValue.nullValue())
                .defineMethod("other", Object.class, Visibility.PUBLIC)
                .intercept(FixedValue.nullValue())
                .make();
        HttpTypeInstrumentation instrumentation = new HttpTypeInstrumentation();

        assertThat(instrumentation.typeMatcher().matches(
                exchangeFunction.getTypeDescription())).isTrue();
        assertThat(instrumentation.typeMatcher().matches(
                implementation.getTypeDescription())).isTrue();
        assertThat(instrumentation.typeMatcher().matches(
                new net.bytebuddy.description.type.TypeDescription.ForLoadedType(String.class)))
                .isFalse();

        AdviceTransformer transformer = (AdviceTransformer) instrumentation.transformer();
        assertThat(transformer.adviceClass()).isEqualTo(HttpExchangeAdvice.class);
        assertThat(transformer.methodMatcher().matches(implementation.getTypeDescription()
                .getDeclaredMethods().filter(named("exchange")).getOnly())).isTrue();
        assertThat(transformer.methodMatcher().matches(implementation.getTypeDescription()
                .getDeclaredMethods().filter(named("other")).getOnly())).isFalse();
    }

    @Test
    void adviceIsFailOpenOnEntryAndExit() throws Exception {
        Method enter = HttpExchangeAdvice.class.getDeclaredMethod("enter");
        Method exit = HttpExchangeAdvice.class.getDeclaredMethod(
                "exit", Object[].class, long.class, Throwable.class);

        assertThat(enter.getAnnotation(Advice.OnMethodEnter.class).suppress())
                .isEqualTo(Throwable.class);
        Advice.OnMethodExit exitAdvice = exit.getAnnotation(Advice.OnMethodExit.class);
        assertThat(exitAdvice.suppress()).isEqualTo(Throwable.class);
        assertThat(exitAdvice.onThrowable()).isEqualTo(Throwable.class);
    }

    @Test
    void extractsThroughPublicInterfaceAndSanitizesBeforeObservation() {
        PluginObservation observation = HttpObservationHelper.describe(
                new PrivateRequest(
                        "g-et",
                        URI.create("https://private.example/orders/7?token=secret#fragment-secret")),
                600,
                false);

        assertThat(observation.type()).isEqualTo("dependency-call");
        assertThat(observation.attributes())
                .containsEntry("http.direction", "outbound")
                .containsEntry("http.method", "G_ET")
                .containsEntry("http.route", "/orders/7");
        assertThat(observation.attributes().get("network.peer.name.hash"))
                .matches("[0-9a-f]{16}");
        assertThat(observation.longFields()).containsEntry("duration.ms", 600L);
        assertThat(observation.booleanFields()).containsEntry("error", false);
        assertThat(observation.toString())
                .doesNotContain("private.example")
                .doesNotContain("token")
                .doesNotContain("secret")
                .doesNotContain("fragment");
    }

    @Test
    void malformedOrMissingRequestDataFailsClosed() {
        PluginObservation malformed = HttpObservationHelper.describe(
                new PrivateRequest(null, "https://private host/orders?token=secret"),
                -10,
                true);
        PluginObservation missing = HttpObservationHelper.describe(null, 1, false);

        assertThat(malformed.attributes())
                .containsEntry("http.method", "UNKNOWN")
                .containsEntry("http.route", "unknown")
                .containsEntry("network.peer.name.hash", "unknown");
        assertThat(malformed.longFields()).containsEntry("duration.ms", 0L);
        assertThat(malformed.booleanFields()).containsEntry("error", true);
        assertThat(malformed.toString())
                .doesNotContain("private host")
                .doesNotContain("token")
                .doesNotContain("secret");
        assertThat(missing.attributes())
                .containsEntry("http.method", "UNKNOWN")
                .containsEntry("http.route", "unknown")
                .containsEntry("network.peer.name.hash", "unknown");
    }

    @Test
    void helperUsesFixedPluginIdAndSuppressesReentrantObservation() {
        AtomicInteger observations = new AtomicInteger();
        AtomicReference<String> pluginId = new AtomicReference<>();
        AtomicReference<PluginObservation> captured = new AtomicReference<>();
        YierLoomBridge.Endpoint endpoint = new YierLoomBridge.Endpoint() {
            @Override
            public boolean observe(String currentPluginId, PluginObservation observation) {
                observations.incrementAndGet();
                pluginId.set(currentPluginId);
                captured.set(observation);
                HttpObservationHelper.observe(
                        new Object[]{new PrivateRequest("POST", URI.create("/nested"))},
                        1,
                        false);
                return true;
            }

            @Override
            public boolean emit(String currentPluginId, DiagnosticEvent event) {
                return false;
            }
        };
        assertThat(YierLoomBridge.install(endpoint)).isTrue();
        try {
            HttpObservationHelper.observe(
                    new Object[]{new PrivateRequest("GET", URI.create("https://example.test/orders"))},
                    12,
                    false);
        } finally {
            assertThat(YierLoomBridge.clear(endpoint)).isTrue();
        }

        assertThat(observations).hasValue(1);
        assertThat(pluginId).hasValue("http");
        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().attributes()).containsEntry("http.route", "/orders");
    }

    @Test
    void emitsSlowAndSummaryEventsUsingOnlyWhitelistedDimensions() throws Exception {
        PluginTestContext context = new PluginTestContext(Map.of(
                "sample-rate", "1.0",
                "slow-threshold", "500ms",
                "summary-interval", "1s",
                "max-events-per-second", "20",
                "top-n", "5",
                "max-tracked-keys", "10"));
        HttpPlugin plugin = new HttpPlugin();
        plugin.start(context);

        context.deliver(PluginObservation.builder("dependency-call")
                .attribute("http.direction", "outbound")
                .attribute("http.method", "GET")
                .attribute("http.route", "/orders/7")
                .attribute("network.peer.name.hash", "0123456789abcdef")
                .attribute("trace.id", "trace-1")
                .attribute("span.id", "span-1")
                .attribute("authorization", "Bearer private-token")
                .attribute("event.action", "forged")
                .longField("duration.ms", 600)
                .booleanField("error", true)
                .build());
        context.runTask("http-summary");

        assertThat(context.scheduledTaskNames()).containsExactly("http-summary");
        assertThat(context.initialDelay("http-summary")).isEqualTo(Duration.ofSeconds(1));
        assertThat(context.delay("http-summary")).isEqualTo(Duration.ofSeconds(1));

        DiagnosticEvent slow = context.singleEvent("http_slow_call");
        assertThat(slow.attributes()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "event.outcome", "threshold",
                "http.direction", "outbound",
                "http.method", "GET",
                "http.route", "/orders/7",
                "network.peer.name.hash", "0123456789abcdef",
                "trace.id", "trace-1",
                "span.id", "span-1"));
        assertThat(slow.longFields()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "duration.ms", 600L,
                "threshold.ms", 500L));
        assertThat(slow.toString())
                .doesNotContain("authorization")
                .doesNotContain("private-token")
                .doesNotContain("forged");

        DiagnosticEvent summary = context.singleEvent("http_call_summary");
        assertThat(summary.attributes()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "event.outcome", "success",
                "http.direction", "outbound",
                "http.method", "GET",
                "http.route", "/orders/7",
                "network.peer.name.hash", "0123456789abcdef"));
        assertThat(summary.longFields()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "call.count", 1L,
                "duration.avg.ms", 600L,
                "duration.max.ms", 600L,
                "duration.p95.ms", 1_000L,
                "error.count", 1L));

        plugin.stop();
        plugin.stop();
        assertThat(context.isTaskCancelled("http-summary")).isTrue();
        int emittedBeforeStop = context.emittedEvents().size();
        context.deliver(HttpObservationHelper.describe(
                new PrivateRequest("GET", URI.create("https://example.test/after-stop")),
                700,
                false));
        context.runTask("http-summary");
        assertThat(context.emittedEvents()).hasSize(emittedBeforeStop);
    }

    @ParameterizedTest
    @MethodSource("invalidSettings")
    void rejectsInvalidSettingsFromInstrumentationAndRuntime(String key, String value) {
        HttpPlugin plugin = new HttpPlugin();
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
    void rejectsUnknownKeysDeterministicallyWithoutExposingValues() {
        HttpPlugin plugin = new HttpPlugin();

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
                new HttpPlugin(),
                PluginConfig.of(Map.of(
                        "enabled", "true",
                        "summary-interval", "1s"))).valid()).isTrue();
    }

    private static Stream<Arguments> invalidSettings() {
        return Stream.of(
                Arguments.of("enabled", "not-a-boolean"),
                Arguments.of("sample-rate", "-0.1"),
                Arguments.of("sample-rate", "1.1"),
                Arguments.of("sample-rate", "NaN"),
                Arguments.of("sample-rate", "Infinity"),
                Arguments.of("max-events-per-second", "-1"),
                Arguments.of("summary-interval", "0s"),
                Arguments.of("top-n", "0"),
                Arguments.of("max-tracked-keys", "0"),
                Arguments.of("slow-threshold", "-1ms"));
    }

    public interface PublicRequest {
        Object method();

        Object url();
    }

    private static final class PrivateRequest implements PublicRequest {
        private final Object method;
        private final Object url;

        private PrivateRequest(Object method, Object url) {
            this.method = method;
            this.url = url;
        }

        @Override
        public Object method() {
            return method;
        }

        @Override
        public Object url() {
            return url;
        }

        @Override
        public String toString() {
            throw new AssertionError("request toString must not be called");
        }
    }
}
