package com.nowcoder.observability.runtimediagnostics.probes.kafka;

import com.nowcoder.observability.runtimediagnostics.config.DiagnosticsConfig;
import com.nowcoder.observability.runtimediagnostics.probes.dependency.DependencyCallKey;
import com.nowcoder.observability.runtimediagnostics.probes.dependency.DependencyDiagnosticsRuntime;
import com.nowcoder.observability.runtimediagnostics.probes.dependency.DependencyTextSanitizer;
import net.bytebuddy.asm.Advice;

import java.util.Map;

public class KafkaTemplateAdvice {

    private static volatile boolean topicNamesEnabled;

    public static void configure(DiagnosticsConfig config) {
        topicNamesEnabled = config != null && config.kafkaTopicNamesEnabled();
    }

    public static String destinationName(String topic, boolean topicNamesEnabled) {
        if (topic == null || topic.isBlank()) {
            return "unknown";
        }
        return topicNamesEnabled ? topic : DependencyTextSanitizer.hash16(topic);
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    static long onEnter() {
        return System.nanoTime();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    static void onExit(
            @Advice.AllArguments Object[] arguments,
            @Advice.Enter long startedAtNanos,
            @Advice.Thrown Throwable thrown
    ) {
        long durationMs = Math.max(0, (System.nanoTime() - startedAtNanos) / 1_000_000L);
        DependencyDiagnosticsRuntime.recordCall(
                "kafka",
                "kafka_slow_call",
                "kafka_produce_summary",
                thrown == null ? "success" : "error",
                new DependencyCallKey("kafka", Map.of(
                        "messaging.operation", "produce",
                        "messaging.destination.name", destinationName(firstTopic(arguments), topicNamesEnabled)
                )),
                durationMs,
                DependencyDiagnosticsRuntime.thresholdMs("kafka"),
                thrown != null,
                Map.of()
        );
    }

    private static String firstTopic(Object[] arguments) {
        if (arguments == null || arguments.length == 0) {
            return null;
        }
        Object first = arguments[0];
        return first instanceof String value ? value : null;
    }
}
