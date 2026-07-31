package com.nowcoder.yierloom.plugins.kafka;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KafkaPluginTest {

    @Test
    void declaresStableDefaultDisabledDescriptorSettingsAndBinding() {
        KafkaPlugin plugin = new KafkaPlugin();
        assertThat(plugin.descriptor().id()).isEqualTo("kafka");
        assertThat(plugin.descriptor().name()).isEqualTo("Kafka Diagnostics");
        assertThat(plugin.descriptor().defaultEnabled()).isFalse();
        assertThat(plugin.descriptor().order()).isEqualTo(230);

        KafkaPlugin.KafkaSettings defaults = KafkaPlugin.KafkaSettings.from(PluginConfig.empty());
        assertThat(defaults.sampleRate()).isEqualTo(1.0);
        assertThat(defaults.maxEventsPerSecond()).isEqualTo(20);
        assertThat(defaults.summaryInterval()).isEqualTo(Duration.ofSeconds(60));
        assertThat(defaults.topN()).isEqualTo(50);
        assertThat(defaults.maxTrackedKeys()).isEqualTo(10_000);
        assertThat(defaults.slowThresholdMs()).isEqualTo(500);
        assertThat(defaults.topicNamesEnabled()).isFalse();

        KafkaInstrumentationModule module = (KafkaInstrumentationModule) plugin
                .instrumentations(PluginConfig.empty()).get(0);
        assertThat(module.id()).isEqualTo("kafka");
        assertThat(module.helperClassNames())
                .containsExactly(KafkaObservationHelper.class.getName());
        AdviceTransformer transformer = (AdviceTransformer) module.typeInstrumentations()
                .get(0).transformer();
        assertThat(transformer.bindings()).singleElement().satisfies(binding -> {
            assertThat(binding.annotationType()).isEqualTo(KafkaTopicNamesEnabled.class);
            assertThat(binding.value()).isEqualTo(false);
        });
    }

    @Test
    void matchesExactKafkaTemplateAndSendMethods() {
        DynamicType.Unloaded<?> template = new ByteBuddy()
                .subclass(Object.class)
                .name("org.springframework.kafka.core.KafkaTemplate")
                .defineMethod("send", Object.class, Visibility.PUBLIC)
                .withParameters(String.class)
                .intercept(net.bytebuddy.implementation.FixedValue.nullValue())
                .defineMethod("receive", Object.class, Visibility.PUBLIC)
                .intercept(net.bytebuddy.implementation.FixedValue.nullValue())
                .make();
        DynamicType.Unloaded<?> subclass = new ByteBuddy()
                .subclass(template.getTypeDescription())
                .name("fixture.CustomKafkaTemplate")
                .make();
        KafkaTypeInstrumentation instrumentation = new KafkaTypeInstrumentation(false);
        assertThat(instrumentation.typeMatcher().matches(template.getTypeDescription())).isTrue();
        assertThat(instrumentation.typeMatcher().matches(subclass.getTypeDescription())).isFalse();
        AdviceTransformer transformer = (AdviceTransformer) instrumentation.transformer();
        assertThat(transformer.methodMatcher().matches(template.getTypeDescription()
                .getDeclaredMethods().filter(named("send")).getOnly())).isTrue();
        assertThat(transformer.methodMatcher().matches(template.getTypeDescription()
                .getDeclaredMethods().filter(named("receive")).getOnly())).isFalse();
    }

    @Test
    void adviceIsFailOpenAndConsumesBoundTopicSetting() throws Exception {
        Method enter = KafkaTemplateAdvice.class.getDeclaredMethod("enter");
        Method exit = KafkaTemplateAdvice.class.getDeclaredMethod(
                "exit", Object[].class, long.class, Throwable.class, boolean.class);
        assertThat(enter.getAnnotation(Advice.OnMethodEnter.class).suppress())
                .isEqualTo(Throwable.class);
        Advice.OnMethodExit annotation = exit.getAnnotation(Advice.OnMethodExit.class);
        assertThat(annotation.suppress()).isEqualTo(Throwable.class);
        assertThat(annotation.onThrowable()).isEqualTo(Throwable.class);
        assertThat(exit.getParameterAnnotations()[3])
                .anyMatch(candidate -> candidate.annotationType() == KafkaTopicNamesEnabled.class);
    }

    @Test
    void hashesTopicByDefaultBeforeObservationAndAllowsExplicitNames() {
        PluginObservation hidden = KafkaObservationHelper.describe(
                new Object[]{"payments-private", "payload-secret"}, 600, true, false);
        assertThat(hidden.attributes())
                .containsEntry("messaging.operation", "produce");
        assertThat(hidden.attributes().get("messaging.destination.name"))
                .matches("[0-9a-f]{16}");
        assertThat(hidden.toString())
                .doesNotContain("payments-private")
                .doesNotContain("payload-secret");

        PluginObservation visible = KafkaObservationHelper.describe(
                new Object[]{"payments-visible", "payload-secret"}, -1, false, true);
        assertThat(visible.attributes())
                .containsEntry("messaging.destination.name", "payments-visible");
        assertThat(visible.longFields()).containsEntry("duration.ms", 0L);
        assertThat(visible.toString()).doesNotContain("payload-secret");

        String surrogateBoundary = "a".repeat(511) + "\uD83D\uDE00tail";
        assertThat(KafkaObservationHelper.describe(
                new Object[]{surrogateBoundary}, 1, false, true)
                .attributes().get("messaging.destination.name"))
                .hasSize(511)
                .doesNotEndWith("\uD83D");
    }

    @Test
    void emitsWhitelistedSlowAndSummaryEventsAndCancelsTask() throws Exception {
        PluginTestContext context = new PluginTestContext(Map.of(
                "slow-threshold", "500ms",
                "summary-interval", "1s"));
        KafkaPlugin plugin = new KafkaPlugin();
        plugin.start(context);
        context.deliver(PluginObservation.builder("dependency-call")
                .attribute("messaging.operation", "produce")
                .attribute("messaging.destination.name", "0123456789abcdef")
                .attribute("trace.id", "trace-kafka")
                .attribute("payload", "private-payload")
                .attribute("event.action", "forged")
                .booleanField("error", true)
                .longField("duration.ms", 500)
                .build());
        context.runTask("kafka-summary");

        DiagnosticEvent slow = context.singleEvent("kafka_slow_call");
        assertThat(slow.attributes()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "event.outcome", "threshold",
                "messaging.operation", "produce",
                "messaging.destination.name", "0123456789abcdef",
                "trace.id", "trace-kafka"));
        assertThat(slow.longFields()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "duration.ms", 500L,
                "threshold.ms", 500L));
        DiagnosticEvent summary = context.singleEvent("kafka_produce_summary");
        assertThat(summary.longFields())
                .containsEntry("call.count", 1L)
                .containsEntry("error.count", 1L)
                .containsEntry("duration.p95.ms", 500L);
        assertThat(context.emittedEvents().toString())
                .doesNotContain("private-payload")
                .doesNotContain("forged");
        plugin.stop();
        plugin.stop();
        assertThat(context.isTaskCancelled("kafka-summary")).isTrue();
    }

    @Test
    void helperUsesFixedPluginIdAndSuppressesReentry() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> pluginId = new AtomicReference<>();
        YierLoomBridge.Endpoint endpoint = new YierLoomBridge.Endpoint() {
            @Override
            public boolean observe(String currentPluginId, PluginObservation observation) {
                calls.incrementAndGet();
                pluginId.set(currentPluginId);
                KafkaObservationHelper.observe(new Object[]{"nested"}, 1, false, false);
                return true;
            }

            @Override
            public boolean emit(String currentPluginId, DiagnosticEvent event) {
                return false;
            }
        };
        assertThat(YierLoomBridge.install(endpoint)).isTrue();
        try {
            KafkaObservationHelper.observe(new Object[]{"outer"}, 1, false, false);
        } finally {
            assertThat(YierLoomBridge.clear(endpoint)).isTrue();
        }
        assertThat(calls).hasValue(1);
        assertThat(pluginId).hasValue("kafka");
    }

    @ParameterizedTest
    @MethodSource("invalidSettings")
    void rejectsInvalidSettingsInInstrumentationAndRuntime(String key, String value) {
        KafkaPlugin plugin = new KafkaPlugin();
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
    void passesBuiltInContractWithBothTopicBindings() {
        assertThat(PluginContractVerifier.verifyProvider(
                new KafkaPlugin(), PluginConfig.empty()).valid()).isTrue();
        assertThat(PluginContractVerifier.verifyProvider(
                new KafkaPlugin(),
                PluginConfig.of(Map.of("topic-names-enabled", "true"))).valid()).isTrue();
    }

    private static Stream<Arguments> invalidSettings() {
        return Stream.of(
                Arguments.of("enabled", "private-true"),
                Arguments.of("topic-names-enabled", "private-false"),
                Arguments.of("sample-rate", "NaN"),
                Arguments.of("sample-rate", "1.1"),
                Arguments.of("max-events-per-second", "-1"),
                Arguments.of("summary-interval", "0s"),
                Arguments.of("top-n", "0"),
                Arguments.of("max-tracked-keys", "0"),
                Arguments.of("slow-threshold", "-1ms"));
    }
}
