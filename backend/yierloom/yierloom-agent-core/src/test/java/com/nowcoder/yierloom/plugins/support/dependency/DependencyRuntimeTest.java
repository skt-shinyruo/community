package com.nowcoder.yierloom.plugins.support.dependency;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.nowcoder.yierloom.api.DiagnosticEvent;
import com.nowcoder.yierloom.api.PluginObservation;
import com.nowcoder.yierloom.plugins.PluginTestContext;
import com.nowcoder.yierloom.plugins.support.DependencyTextSanitizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DependencyRuntimeTest {

    @Test
    void emitsSlowAndCumulativeSummaryEventsWithOnlyDeclaredDimensions() throws Exception {
        PluginTestContext context = new PluginTestContext(Map.of());
        DependencyRuntime runtime = runtime(context, 200, 20, 50, 10);

        context.deliver(observation(250, true, "select", "trace-1")
                .attribute("event.action", "forged-action")
                .attribute("sql", "select private_secret")
                .build());
        context.deliver(observation(150, false, "select", "trace-2").build());
        context.runTask("jdbc-summary");

        DiagnosticEvent slow = context.singleEvent("jdbc_slow_call");
        assertThat(slow.attributes()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "event.outcome", "threshold",
                "db.system", "jdbc",
                "db.operation", "select",
                "db.statement.hash", "0123456789abcdef",
                "trace.id", "trace-1"));
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
                "call.count", 2L,
                "duration.avg.ms", 200L,
                "duration.max.ms", 250L,
                "duration.p95.ms", 500L,
                "error.count", 1L));
        assertThat(context.emittedEvents().toString())
                .doesNotContain("forged-action")
                .doesNotContain("private_secret")
                .doesNotContain("trace-2");

        context.runTask("jdbc-summary");
        assertThat(context.events("jdbc_call_summary")).hasSize(2);
        runtime.close();
        assertThat(context.isTaskCancelled("jdbc-summary")).isTrue();
    }

    @Test
    void rateLimitDoesNotDisableAggregationAndCloseFencesEmission() throws Exception {
        PluginTestContext context = new PluginTestContext(Map.of());
        DependencyRuntime runtime = runtime(context, 0, 0, 50, 10);

        context.deliver(observation(-10, false, "select", null).build());
        context.runTask("jdbc-summary");
        assertThat(context.events("jdbc_slow_call")).isEmpty();
        assertThat(context.singleEvent("jdbc_call_summary").longFields())
                .containsEntry("call.count", 1L)
                .containsEntry("duration.avg.ms", 0L);

        runtime.close();
        runtime.close();
        context.deliver(observation(500, true, "update", "late-trace").build());
        context.runTask("jdbc-summary");
        assertThat(context.emittedEvents()).hasSize(1);
    }

    @Test
    void trackedKeyLimitRetainsExistingKeysAndTopNUsesMaximumLatency() throws Exception {
        PluginTestContext context = new PluginTestContext(Map.of());
        DependencyRuntime runtime = runtime(context, 10_000, 20, 1, 2);

        context.deliver(observation(10, false, "select", null).build());
        context.deliver(observation(300, false, "update", null).build());
        context.deliver(observation(900, false, "delete", null).build());
        context.deliver(observation(30, true, "select", null).build());
        context.runTask("jdbc-summary");

        DiagnosticEvent summary = context.singleEvent("jdbc_call_summary");
        assertThat(summary.attributes()).containsEntry("db.operation", "update");
        assertThat(summary.longFields()).containsEntry("call.count", 1L);
        runtime.close();
    }

    @Test
    void rejectsReservedOrDuplicateDimensionDeclarations() {
        PluginTestContext context = new PluginTestContext(Map.of());

        assertThatThrownBy(() -> start(context, List.of("event.action")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dimensionKeys");
        assertThatThrownBy(() -> start(
                new PluginTestContext(Map.of()),
                List.of("db.system", "db.system")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dimensionKeys");
    }

    @Test
    void sanitizerProducesStableHashesAndBoundsControlCharacters() {
        assertThat(DependencyTextSanitizer.hash16("private.example"))
                .matches("[0-9a-f]{16}")
                .isEqualTo(DependencyTextSanitizer.hash16("private.example"));
        assertThat(DependencyTextSanitizer.hash16(" ")).isEqualTo("unknown");
        assertThat(DependencyTextSanitizer.dimension("line\nsecret"))
                .isEqualTo("line_secret");
        String surrogateBoundary = "a".repeat(511) + "\uD83D\uDE00tail";
        assertThat(DependencyTextSanitizer.dimension(surrogateBoundary))
                .hasSize(511)
                .doesNotEndWith("\uD83D");
    }

    private static DependencyRuntime runtime(
            PluginTestContext context,
            long threshold,
            int rate,
            int topN,
            int maxKeys
    ) {
        return DependencyRuntime.start(
                context,
                "jdbc",
                "jdbc_slow_call",
                "jdbc_call_summary",
                List.of("db.system", "db.operation", "db.statement.hash"),
                1.0,
                rate,
                Duration.ofSeconds(1),
                topN,
                maxKeys,
                threshold);
    }

    private static DependencyRuntime start(PluginTestContext context, List<String> dimensions) {
        return DependencyRuntime.start(
                context,
                "test",
                "test_slow",
                "test_summary",
                dimensions,
                1.0,
                1,
                Duration.ofSeconds(1),
                1,
                1,
                1);
    }

    private static PluginObservation.Builder observation(
            long duration,
            boolean error,
            String operation,
            String traceId
    ) {
        PluginObservation.Builder observation = PluginObservation.builder("dependency-call")
                .attribute("db.system", "jdbc")
                .attribute("db.operation", operation)
                .attribute("db.statement.hash", "0123456789abcdef")
                .booleanField("error", error)
                .longField("duration.ms", duration);
        if (traceId != null) {
            observation.attribute("trace.id", traceId);
        }
        return observation;
    }
}
