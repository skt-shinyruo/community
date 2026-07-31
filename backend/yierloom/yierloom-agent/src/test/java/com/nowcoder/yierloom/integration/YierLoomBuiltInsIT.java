package com.nowcoder.yierloom.integration;

import java.nio.file.Path;
import java.util.List;

import com.example.yierloom.integration.AgentTargetMain;
import com.nowcoder.yierloom.integration.support.ForkResult;
import com.nowcoder.yierloom.integration.support.ForkedJvm;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class YierLoomBuiltInsIT {
    private static final Path AGENT_JAR = Path.of("target", "yierloom-agent.jar")
            .toAbsolutePath()
            .normalize();
    private static final List<String> EXPECTED_ACTIONS = List.of(
            "method_latency_summary",
            "method_slow_call",
            "exception_observed",
            "thread_snapshot",
            "jvm_runtime_summary",
            "http_slow_call",
            "http_call_summary",
            "jdbc_slow_call",
            "jdbc_call_summary",
            "redis_slow_call",
            "redis_call_summary",
            "kafka_slow_call",
            "kafka_produce_summary");

    private static final String PRIVATE_HTTP_HOST = "private-host.yierloom.invalid";
    private static final String PRIVATE_HTTP_QUERY = "query-secret-yierloom";
    private static final String PRIVATE_SQL_LITERAL = "sql-secret-yierloom";
    private static final String PRIVATE_REDIS_KEY = "redis-secret-yierloom:key";
    private static final String PRIVATE_REDIS_VALUE = "redis-value-secret-yierloom";
    private static final String PRIVATE_KAFKA_TOPIC = "kafka-secret-yierloom";
    private static final String PRIVATE_KAFKA_PAYLOAD = "kafka-payload-secret-yierloom";

    @Test
    void packagedAgentRunsEveryBuiltInWithoutChangingTargetFailuresOrLeakingInputs() throws Exception {
        ForkResult result = runBuiltInsWithDefaultPrivacy();

        assertSuccessfulFork(result);
        assertThat(result.stdout())
                .contains("EXCEPTION_TYPE=java.lang.IllegalStateException")
                .contains("EXCEPTION_MESSAGE=target-boom")
                .contains("EXCEPTION_SAME_INSTANCE=true")
                .contains("TARGET_COMPLETED");
        for (String action : EXPECTED_ACTIONS) {
            assertThat(result.stdout())
                    .as("packaged agent output should contain action %s", action)
                    .contains("\"event.action\":\"" + action + "\"");
        }
        assertThat(result.combinedOutput()).doesNotContain(
                PRIVATE_HTTP_HOST,
                PRIVATE_HTTP_QUERY,
                PRIVATE_SQL_LITERAL,
                PRIVATE_REDIS_KEY,
                PRIVATE_REDIS_VALUE,
                PRIVATE_KAFKA_TOPIC,
                PRIVATE_KAFKA_PAYLOAD,
                "NoClassDefFoundError",
                "LinkageError");
    }

    @Test
    void kafkaTopicNameRequiresAnExplicitOptInAndPayloadRemainsPrivate() throws Exception {
        ForkResult result = ForkedJvm.run(
                AGENT_JAR,
                kafkaOptInArguments(),
                AgentTargetMain.class);

        assertSuccessfulFork(result);
        assertThat(result.stdout())
                .contains("TARGET_COMPLETED")
                .contains("\"diagnostic.plugin.id\":\"kafka\"")
                .contains("\"messaging.destination.name\":\"" + PRIVATE_KAFKA_TOPIC + "\"");
        assertThat(result.combinedOutput()).doesNotContain(PRIVATE_KAFKA_PAYLOAD);
    }

    private static ForkResult runBuiltInsWithDefaultPrivacy() throws Exception {
        return ForkedJvm.run(
                AGENT_JAR,
                builtInArguments(),
                AgentTargetMain.class);
    }

    private static String kafkaOptInArguments() {
        return String.join(",",
                "yierloom.enabled=true",
                "yierloom.service.name=yierloom-kafka-opt-in-it",
                "yierloom.plugins.method.enabled=false",
                "yierloom.plugins.exception.enabled=false",
                "yierloom.plugins.thread.enabled=false",
                "yierloom.plugins.jvm.enabled=false",
                "yierloom.plugins.http.enabled=false",
                "yierloom.plugins.jdbc.enabled=false",
                "yierloom.plugins.redis.enabled=false",
                dependencyArguments("kafka"),
                "yierloom.plugins.kafka.topic-names-enabled=true");
    }

    private static String builtInArguments() {
        return String.join(",",
                "yierloom.enabled=true",
                "yierloom.service.name=yierloom-built-ins-it",
                "yierloom.plugins.method.enabled=true",
                "yierloom.plugins.method.includes=com.example.yierloom.integration.*",
                "yierloom.plugins.method.sample-rate=1.0",
                "yierloom.plugins.method.max-events-per-second=100",
                "yierloom.plugins.method.summary-interval=20ms",
                "yierloom.plugins.method.slow-threshold=0ms",
                "yierloom.plugins.method.top-n=10",
                "yierloom.plugins.method.max-tracked-keys=100",
                "yierloom.plugins.exception.enabled=true",
                "yierloom.plugins.exception.includes=com.example.yierloom.integration.*",
                "yierloom.plugins.exception.sample-rate=1.0",
                "yierloom.plugins.exception.max-events-per-second=100",
                "yierloom.plugins.thread.enabled=true",
                "yierloom.plugins.thread.snapshot-interval=20ms",
                "yierloom.plugins.jvm.enabled=true",
                "yierloom.plugins.jvm.summary-interval=20ms",
                dependencyArguments("http"),
                dependencyArguments("jdbc"),
                dependencyArguments("redis"),
                dependencyArguments("kafka"),
                "yierloom.plugins.kafka.topic-names-enabled=false");
    }

    private static String dependencyArguments(String pluginId) {
        String prefix = "yierloom.plugins." + pluginId + ".";
        return String.join(",",
                prefix + "enabled=true",
                prefix + "sample-rate=1.0",
                prefix + "max-events-per-second=100",
                prefix + "summary-interval=20ms",
                prefix + "slow-threshold=0ms",
                prefix + "top-n=10",
                prefix + "max-tracked-keys=100");
    }

    private static void assertSuccessfulFork(ForkResult result) {
        assertThat(result.exitCode()).as(result.combinedOutput()).isZero();
    }
}
