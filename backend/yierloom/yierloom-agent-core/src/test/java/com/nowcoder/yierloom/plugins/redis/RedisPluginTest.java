package com.nowcoder.yierloom.plugins.redis;

import java.lang.reflect.Method;
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

class RedisPluginTest {

    @Test
    void declaresStableDefaultDisabledDescriptorAndSettings() {
        RedisPlugin plugin = new RedisPlugin();

        assertThat(plugin.descriptor().id()).isEqualTo("redis");
        assertThat(plugin.descriptor().name()).isEqualTo("Redis Diagnostics");
        assertThat(plugin.descriptor().version()).isEqualTo("1.0.0");
        assertThat(plugin.descriptor().apiVersion()).isEqualTo("1.0.0");
        assertThat(plugin.descriptor().defaultEnabled()).isFalse();
        assertThat(plugin.descriptor().order()).isEqualTo(220);

        RedisInstrumentationModule module = (RedisInstrumentationModule) plugin
                .instrumentations(PluginConfig.empty())
                .get(0);
        assertThat(module.id()).isEqualTo("redis");
        assertThat(module.typeInstrumentations()).singleElement();
        assertThat(module.helperClassNames())
                .containsExactly(RedisObservationHelper.class.getName());

        RedisPlugin.RedisSettings settings = RedisPlugin.RedisSettings.from(PluginConfig.empty());
        assertThat(settings.sampleRate()).isEqualTo(1.0);
        assertThat(settings.maxEventsPerSecond()).isEqualTo(20);
        assertThat(settings.summaryInterval()).isEqualTo(Duration.ofSeconds(60));
        assertThat(settings.topN()).isEqualTo(50);
        assertThat(settings.maxTrackedKeys()).isEqualTo(10_000);
        assertThat(settings.slowThresholdMs()).isEqualTo(100);
    }

    @Test
    void matchesOnlyExactRedisTemplateAndExecutionMethods() {
        DynamicType.Unloaded<?> redisTemplate = new ByteBuddy()
                .subclass(Object.class)
                .name("org.springframework.data.redis.core.RedisTemplate")
                .defineMethod("execute", Object.class, Visibility.PUBLIC)
                .withParameters(Object.class)
                .intercept(FixedValue.nullValue())
                .defineMethod("executePipelined", Object.class, Visibility.PUBLIC)
                .withParameters(Object.class)
                .intercept(FixedValue.nullValue())
                .defineMethod("executeWithStickyConnection", Object.class, Visibility.PUBLIC)
                .withParameters(Object.class)
                .intercept(FixedValue.nullValue())
                .defineMethod("other", Object.class, Visibility.PUBLIC)
                .intercept(FixedValue.nullValue())
                .make();
        DynamicType.Unloaded<?> subclass = new ByteBuddy()
                .subclass(redisTemplate.getTypeDescription())
                .name("fixture.RedisTemplateSubclass")
                .make();
        RedisTypeInstrumentation instrumentation = new RedisTypeInstrumentation();

        assertThat(instrumentation.typeMatcher().matches(
                redisTemplate.getTypeDescription())).isTrue();
        assertThat(instrumentation.typeMatcher().matches(
                subclass.getTypeDescription())).isFalse();

        AdviceTransformer transformer = (AdviceTransformer) instrumentation.transformer();
        assertThat(transformer.adviceClass()).isEqualTo(RedisTemplateAdvice.class);
        assertThat(transformer.methodMatcher().matches(redisTemplate.getTypeDescription()
                .getDeclaredMethods().filter(named("execute")).getOnly())).isTrue();
        assertThat(transformer.methodMatcher().matches(redisTemplate.getTypeDescription()
                .getDeclaredMethods().filter(named("executePipelined")).getOnly())).isTrue();
        assertThat(transformer.methodMatcher().matches(redisTemplate.getTypeDescription()
                .getDeclaredMethods().filter(named("executeWithStickyConnection")).getOnly()))
                .isTrue();
        assertThat(transformer.methodMatcher().matches(redisTemplate.getTypeDescription()
                .getDeclaredMethods().filter(named("other")).getOnly())).isFalse();
    }

    @Test
    void adviceIsFailOpenOnEntryAndExit() throws Exception {
        Method enter = RedisTemplateAdvice.class.getDeclaredMethod("enter");
        Method exit = RedisTemplateAdvice.class.getDeclaredMethod(
                "exit", String.class, Object[].class, long.class, Throwable.class);

        assertThat(enter.getAnnotation(Advice.OnMethodEnter.class).suppress())
                .isEqualTo(Throwable.class);
        Advice.OnMethodExit exitAdvice = exit.getAnnotation(Advice.OnMethodExit.class);
        assertThat(exitAdvice.suppress()).isEqualTo(Throwable.class);
        assertThat(exitAdvice.onThrowable()).isEqualTo(Throwable.class);
    }

    @Test
    void normalizesCommandAndHashesOnlyTheFirstStringNamespace() {
        Object explosive = new Object() {
            @Override
            public String toString() {
                throw new AssertionError("non-String arguments must not be inspected");
            }
        };
        PluginObservation first = RedisObservationHelper.describe(
                "execute-pipelined",
                new Object[]{explosive, "user:token:secret", "private-value"},
                150,
                true);
        PluginObservation sameNamespace = RedisObservationHelper.describe(
                "execute", new Object[]{"user:another-secret"}, 1, false);
        PluginObservation otherNamespace = RedisObservationHelper.describe(
                "execute", new Object[]{"orders:another-secret"}, 1, false);

        assertThat(first.type()).isEqualTo("dependency-call");
        assertThat(first.attributes())
                .containsEntry("redis.command", "EXECUTE_PIPELINED")
                .containsOnlyKeys("redis.command", "redis.namespace.hash");
        assertThat(first.attributes().get("redis.namespace.hash"))
                .matches("[0-9a-f]{16}")
                .isEqualTo(sameNamespace.attributes().get("redis.namespace.hash"))
                .isNotEqualTo(otherNamespace.attributes().get("redis.namespace.hash"));
        assertThat(first.longFields()).containsEntry("duration.ms", 150L);
        assertThat(first.booleanFields()).containsEntry("error", true);
        assertThat(first.toString())
                .doesNotContain("user")
                .doesNotContain("token")
                .doesNotContain("secret")
                .doesNotContain("private-value")
                .doesNotContain("redis.key")
                .doesNotContain("redis.keyspace");
    }

    @Test
    void missingDataFailsClosedWithoutInventingRedisData() {
        PluginObservation observation = RedisObservationHelper.describe(null, null, -10, false);

        assertThat(observation.attributes()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "redis.command", "UNKNOWN",
                "redis.namespace.hash", "unknown"));
        assertThat(observation.longFields()).containsEntry("duration.ms", 0L);
        assertThat(observation.booleanFields()).containsEntry("error", false);
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
                RedisObservationHelper.observe(
                        "execute", new Object[]{"nested:private"}, 1, false);
                return true;
            }

            @Override
            public boolean emit(String currentPluginId, DiagnosticEvent event) {
                return false;
            }
        };
        assertThat(YierLoomBridge.install(endpoint)).isTrue();
        try {
            RedisObservationHelper.observe(
                    "execute", new Object[]{"user:private"}, 12, false);
        } finally {
            assertThat(YierLoomBridge.clear(endpoint)).isTrue();
        }

        assertThat(observations).hasValue(1);
        assertThat(pluginId).hasValue("redis");
        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().attributes()).containsEntry("redis.command", "EXECUTE");
    }

    @Test
    void emitsSlowAndSummaryEventsUsingOnlyWhitelistedDimensions() throws Exception {
        PluginTestContext context = new PluginTestContext(Map.of(
                "sample-rate", "1.0",
                "slow-threshold", "100ms",
                "summary-interval", "1s",
                "max-events-per-second", "20",
                "top-n", "5",
                "max-tracked-keys", "10"));
        RedisPlugin plugin = new RedisPlugin();
        plugin.start(context);

        context.deliver(PluginObservation.builder("dependency-call")
                .attribute("redis.command", "EXECUTE")
                .attribute("redis.namespace.hash", "0123456789abcdef")
                .attribute("trace.id", "trace-1")
                .attribute("span.id", "span-1")
                .attribute("redis.key", "private-key")
                .attribute("redis.value", "private-value")
                .attribute("event.action", "forged")
                .longField("duration.ms", 150)
                .booleanField("error", true)
                .build());
        context.runTask("redis-summary");

        assertThat(context.scheduledTaskNames()).containsExactly("redis-summary");
        assertThat(context.initialDelay("redis-summary")).isEqualTo(Duration.ofSeconds(1));
        assertThat(context.delay("redis-summary")).isEqualTo(Duration.ofSeconds(1));

        DiagnosticEvent slow = context.singleEvent("redis_slow_call");
        assertThat(slow.attributes()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "event.outcome", "threshold",
                "redis.command", "EXECUTE",
                "redis.namespace.hash", "0123456789abcdef",
                "trace.id", "trace-1",
                "span.id", "span-1"));
        assertThat(slow.longFields()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "duration.ms", 150L,
                "threshold.ms", 100L));
        assertThat(slow.toString())
                .doesNotContain("private-key")
                .doesNotContain("private-value")
                .doesNotContain("forged")
                .doesNotContain("redis.key");

        DiagnosticEvent summary = context.singleEvent("redis_call_summary");
        assertThat(summary.attributes()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "event.outcome", "success",
                "redis.command", "EXECUTE",
                "redis.namespace.hash", "0123456789abcdef"));
        assertThat(summary.longFields()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "call.count", 1L,
                "duration.avg.ms", 150L,
                "duration.max.ms", 150L,
                "duration.p95.ms", 200L,
                "error.count", 1L));

        plugin.stop();
        plugin.stop();
        assertThat(context.isTaskCancelled("redis-summary")).isTrue();
        int emittedBeforeStop = context.emittedEvents().size();
        context.deliver(RedisObservationHelper.describe(
                "execute", new Object[]{"after-stop:private"}, 200, false));
        context.runTask("redis-summary");
        assertThat(context.emittedEvents()).hasSize(emittedBeforeStop);
    }

    @ParameterizedTest
    @MethodSource("invalidSettings")
    void rejectsInvalidSettingsFromInstrumentationAndRuntime(String key, String value) {
        RedisPlugin plugin = new RedisPlugin();
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
        RedisPlugin plugin = new RedisPlugin();

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
                new RedisPlugin(),
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
}
