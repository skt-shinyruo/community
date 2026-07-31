package com.nowcoder.yierloom.plugins.jdbc;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Statement;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
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
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcPluginTest {

    @Test
    void declaresStableDefaultDisabledDescriptorSettingsAndHelpers() {
        JdbcPlugin plugin = new JdbcPlugin();

        assertThat(plugin.descriptor().id()).isEqualTo("jdbc");
        assertThat(plugin.descriptor().name()).isEqualTo("JDBC Diagnostics");
        assertThat(plugin.descriptor().version()).isEqualTo("1.0.0");
        assertThat(plugin.descriptor().apiVersion()).isEqualTo("1.0.0");
        assertThat(plugin.descriptor().defaultEnabled()).isFalse();
        assertThat(plugin.descriptor().order()).isEqualTo(210);

        JdbcInstrumentationModule module = (JdbcInstrumentationModule) plugin
                .instrumentations(PluginConfig.empty())
                .get(0);
        assertThat(module.id()).isEqualTo("jdbc");
        assertThat(module.typeInstrumentations()).singleElement();
        assertThat(module.helperClassNames())
                .containsExactly(JdbcObservationHelper.class.getName());

        JdbcPlugin.JdbcSettings settings = JdbcPlugin.JdbcSettings.from(PluginConfig.empty());
        assertThat(settings.sampleRate()).isEqualTo(1.0);
        assertThat(settings.maxEventsPerSecond()).isEqualTo(20);
        assertThat(settings.summaryInterval()).isEqualTo(Duration.ofSeconds(60));
        assertThat(settings.topN()).isEqualTo(50);
        assertThat(settings.maxTrackedKeys()).isEqualTo(10_000);
        assertThat(settings.slowThresholdMs()).isEqualTo(200);
    }

    @Test
    void matchesOnlyNonJdkStatementImplementationsAndExactExecutionMethods() throws Exception {
        JdbcTypeInstrumentation instrumentation = new JdbcTypeInstrumentation();
        Class<?> proxyType = Proxy.newProxyInstance(
                JdbcPluginTest.class.getClassLoader(),
                new Class<?>[]{Statement.class},
                (proxy, method, arguments) -> null).getClass();

        assertThat(instrumentation.typeMatcher().matches(
                TypeDescription.ForLoadedType.of(proxyType))).isTrue();
        assertThat(instrumentation.typeMatcher().matches(
                TypeDescription.ForLoadedType.of(Statement.class))).isFalse();
        assertThat(instrumentation.typeMatcher().matches(
                TypeDescription.ForLoadedType.of(String.class))).isFalse();

        AdviceTransformer transformer = (AdviceTransformer) instrumentation.transformer();
        assertThat(matches(transformer, Statement.class.getMethod("execute", String.class))).isTrue();
        assertThat(matches(transformer, Statement.class.getMethod("executeQuery", String.class))).isTrue();
        assertThat(matches(transformer, Statement.class.getMethod("executeUpdate", String.class))).isTrue();
        assertThat(matches(transformer, Statement.class.getMethod("executeLargeUpdate", String.class))).isTrue();
        assertThat(matches(transformer, Statement.class.getMethod("executeBatch"))).isTrue();
        assertThat(matches(transformer, Statement.class.getMethod("close"))).isFalse();
        assertThat(matches(transformer, Object.class.getMethod("toString"))).isFalse();
    }

    @Test
    void adviceEnterAndExitSuppressThrowableAndExitObservesThrownCalls() throws Exception {
        Method enterMethod = JdbcStatementAdvice.class.getDeclaredMethod("enter");
        Advice.OnMethodEnter enter = enterMethod.getAnnotation(Advice.OnMethodEnter.class);
        assertThat(enter).isNotNull();
        assertThat(enter.suppress()).isEqualTo(Throwable.class);

        Method exitMethod = JdbcStatementAdvice.class.getDeclaredMethod(
                "exit", Object[].class, long.class, Throwable.class);
        Advice.OnMethodExit exit = exitMethod.getAnnotation(Advice.OnMethodExit.class);
        assertThat(exit).isNotNull();
        assertThat(exit.suppress()).isEqualTo(Throwable.class);
        assertThat(exit.onThrowable()).isEqualTo(Throwable.class);
    }

    @Test
    void hashesNormalizedSqlWithoutLiteralsBindValuesOrRawStatement() {
        Object explosive = new Object() {
            @Override
            public String toString() {
                throw new AssertionError("non-SQL argument was stringified");
            }
        };

        PluginObservation observation = JdbcObservationHelper.describe(
                new Object[]{
                        explosive,
                        "select * from account where id=42 and name='secret'",
                        "ignored-string-bind-value"
                },
                250,
                false);

        assertThat(observation.type()).isEqualTo("dependency-call");
        assertThat(observation.attributes())
                .containsEntry("db.system", "jdbc")
                .containsEntry("db.operation", "select")
                .containsEntry("db.statement.hash", "086538a5aaa068b8");
        assertThat(observation.attributes().keySet()).isSubsetOf(
                "db.system", "db.operation", "db.statement.hash", "trace.id", "span.id");
        assertThat(observation.booleanFields()).containsExactly(Map.entry("error", false));
        assertThat(observation.longFields()).containsExactly(Map.entry("duration.ms", 250L));
        assertThat(observation.toString())
                .doesNotContain("42")
                .doesNotContain("secret")
                .doesNotContain("account")
                .doesNotContain("ignored-string-bind-value");

        PluginObservation blank = JdbcObservationHelper.describe(new Object[]{explosive}, -1, true);
        assertThat(blank.attributes())
                .containsEntry("db.operation", "unknown")
                .containsEntry("db.statement.hash", "unknown");
        assertThat(blank.longFields()).containsEntry("duration.ms", 0L);
        assertThat(blank.booleanFields()).containsEntry("error", true);

        PluginObservation unknown = JdbcObservationHelper.describe(
                new Object[]{"create table private_table(id int)"}, 1, false);
        assertThat(unknown.attributes())
                .containsEntry("db.operation", "unknown");
        assertThat(unknown.attributes().get("db.statement.hash")).matches("[0-9a-f]{16}");
        assertThat(unknown.toString()).doesNotContain("private_table");
    }

    @Test
    void emitsOnlyWhitelistedSlowAndSummaryFields() throws Exception {
        PluginTestContext context = new PluginTestContext(Map.of(
                "slow-threshold", "200ms",
                "summary-interval", "1s",
                "sample-rate", "1.0",
                "max-events-per-second", "20"));
        JdbcPlugin plugin = new JdbcPlugin();
        plugin.start(context);

        context.deliver(PluginObservation.builder("dependency-call")
                .attribute("db.system", "jdbc")
                .attribute("db.operation", "select")
                .attribute("db.statement.hash", "0123456789abcdef")
                .attribute("trace.id", "trace-jdbc")
                .attribute("span.id", "span-jdbc")
                .attribute("event.action", "forged-action")
                .attribute("event.outcome", "forged-outcome")
                .attribute("sql", "select secret from private_table")
                .attribute("message", "private-message")
                .booleanField("error", true)
                .longField("duration.ms", 250)
                .build());
        context.runTask("jdbc-summary");

        DiagnosticEvent slow = context.singleEvent("jdbc_slow_call");
        assertThat(slow.attributes()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "event.outcome", "threshold",
                "db.system", "jdbc",
                "db.operation", "select",
                "db.statement.hash", "0123456789abcdef",
                "trace.id", "trace-jdbc",
                "span.id", "span-jdbc"));
        assertThat(slow.longFields()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "duration.ms", 250L,
                "threshold.ms", 200L));

        DiagnosticEvent summary = context.singleEvent("jdbc_call_summary");
        assertThat(summary.attributes()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "event.outcome", "success",
                "db.system", "jdbc",
                "db.operation", "select",
                "db.statement.hash", "0123456789abcdef"));
        assertThat(summary.longFields()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "call.count", 1L,
                "duration.avg.ms", 250L,
                "duration.max.ms", 250L,
                "duration.p95.ms", 500L,
                "error.count", 1L));
        assertThat(context.emittedEvents().toString())
                .doesNotContain("forged-action")
                .doesNotContain("forged-outcome")
                .doesNotContain("private_table")
                .doesNotContain("private-message");

        assertThat(context.scheduledTaskNames()).containsExactly("jdbc-summary");
        assertThat(context.initialDelay("jdbc-summary")).isEqualTo(Duration.ofSeconds(1));
        assertThat(context.delay("jdbc-summary")).isEqualTo(Duration.ofSeconds(1));
        plugin.stop();
        assertThat(context.isTaskCancelled("jdbc-summary")).isTrue();
    }

    @Test
    void rateLimitAffectsOnlySlowEventsAndStopFencesFurtherEmission() throws Exception {
        PluginTestContext context = new PluginTestContext(Map.of(
                "slow-threshold", "0ms",
                "summary-interval", "1s",
                "sample-rate", "1.0",
                "max-events-per-second", "0"));
        JdbcPlugin plugin = new JdbcPlugin();
        plugin.start(context);

        context.deliver(jdbcObservation("select", "aaaaaaaaaaaaaaaa", 10));
        context.deliver(PluginObservation.builder("unrelated").longField("duration.ms", 20).build());
        context.deliver(jdbcObservation("select", "aaaaaaaaaaaaaaaa", 30));
        context.runTask("jdbc-summary");

        assertThat(context.events("jdbc_slow_call")).isEmpty();
        assertThat(context.singleEvent("jdbc_call_summary").longFields())
                .containsEntry("call.count", 2L)
                .containsEntry("duration.avg.ms", 20L);

        plugin.stop();
        plugin.stop();
        context.deliver(jdbcObservation("select", "aaaaaaaaaaaaaaaa", 50));
        context.runTask("jdbc-summary");
        assertThat(context.events("jdbc_call_summary")).hasSize(1);
    }

    @Test
    void samplingAndTrackedKeyBoundsAreEnforced() throws Exception {
        PluginTestContext sampledOut = new PluginTestContext(Map.of(
                "sample-rate", "0",
                "summary-interval", "1s"));
        JdbcPlugin sampledPlugin = new JdbcPlugin();
        sampledPlugin.start(sampledOut);
        sampledOut.deliver(jdbcObservation("select", "aaaaaaaaaaaaaaaa", 250));
        sampledOut.runTask("jdbc-summary");
        assertThat(sampledOut.emittedEvents()).isEmpty();
        sampledPlugin.stop();

        PluginTestContext bounded = new PluginTestContext(Map.of(
                "sample-rate", "1.0",
                "slow-threshold", "1h",
                "summary-interval", "1s",
                "max-tracked-keys", "1"));
        JdbcPlugin boundedPlugin = new JdbcPlugin();
        boundedPlugin.start(bounded);
        bounded.deliver(jdbcObservation("select", "aaaaaaaaaaaaaaaa", 10));
        bounded.deliver(jdbcObservation("update", "bbbbbbbbbbbbbbbb", 20));
        bounded.deliver(jdbcObservation("select", "aaaaaaaaaaaaaaaa", 30));
        bounded.runTask("jdbc-summary");

        DiagnosticEvent summary = bounded.singleEvent("jdbc_call_summary");
        assertThat(summary.attributes())
                .containsEntry("db.operation", "select")
                .containsEntry("db.statement.hash", "aaaaaaaaaaaaaaaa");
        assertThat(summary.longFields())
                .containsEntry("call.count", 2L)
                .containsEntry("duration.avg.ms", 20L);
        boundedPlugin.stop();
    }

    @Test
    void helperSuppressesReentrantObservationAndToleratesClassLoaderFailures() {
        AtomicInteger observations = new AtomicInteger();
        AtomicReference<String> pluginId = new AtomicReference<>();
        AtomicReference<PluginObservation> captured = new AtomicReference<>();
        YierLoomBridge.Endpoint endpoint = new YierLoomBridge.Endpoint() {
            @Override
            public boolean observe(String candidatePluginId, PluginObservation observation) {
                observations.incrementAndGet();
                pluginId.set(candidatePluginId);
                captured.set(observation);
                JdbcObservationHelper.observe(new Object[]{"select 7"}, 1, false);
                return true;
            }

            @Override
            public boolean emit(String candidatePluginId, DiagnosticEvent event) {
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
            JdbcObservationHelper.observe(new Object[]{"select 42"}, 1, false);
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
            assertThat(YierLoomBridge.clear(endpoint)).isTrue();
        }

        assertThat(observations).hasValue(1);
        assertThat(pluginId).hasValue("jdbc");
        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().attributes())
                .containsEntry("db.operation", "select")
                .doesNotContainKeys("trace.id", "span.id");
    }

    @ParameterizedTest
    @MethodSource("invalidSettings")
    void rejectsExplicitInvalidSettingsFromInstrumentationAndRuntime(
            String key,
            String value
    ) {
        JdbcPlugin plugin = new JdbcPlugin();
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
    void rejectsUnknownKeysDeterministicallyWithoutLeakingValues() {
        JdbcPlugin plugin = new JdbcPlugin();

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
                new JdbcPlugin(),
                PluginConfig.of(Map.of("summary-interval", "1s"))).valid()).isTrue();
    }

    private static boolean matches(AdviceTransformer transformer, Method method) {
        return transformer.methodMatcher().matches(new MethodDescription.ForLoadedMethod(method));
    }

    private static PluginObservation jdbcObservation(
            String operation,
            String statementHash,
            long durationMs
    ) {
        return PluginObservation.builder("dependency-call")
                .attribute("db.system", "jdbc")
                .attribute("db.operation", operation)
                .attribute("db.statement.hash", statementHash)
                .booleanField("error", false)
                .longField("duration.ms", durationMs)
                .build();
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
