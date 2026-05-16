package com.nowcoder.community.infra.observability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.observability.logging.RuntimeLogEvent;
import com.nowcoder.community.common.observability.logging.RuntimeLogFields;
import com.nowcoder.community.common.observability.logging.RuntimeLogWriter;
import com.nowcoder.community.common.trace.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class RuntimeObservabilityIntegrationTest {

    private static final String SERVICE_VERSION = "test-service-version";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LoggingSystem loggingSystem = LoggingSystem.get(getClass().getClassLoader());

    @AfterEach
    void tearDown() {
        TraceContext.clear();
        loggingSystem.cleanUp();
    }

    @Test
    void runtimeEventsAreWrittenAsStructuredJson(CapturedOutput output) {
        initializeProductionLogging("community-app");
        TraceContext.set("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "bbbbbbbbbbbbbbbb");
        RuntimeLogWriter writer = new RuntimeLogWriter(LoggerFactory.getLogger("com.nowcoder.community.runtime"));

        writer.warn(RuntimeLogEvent.builder("runtime", "jvm_gc_pause_threshold", "threshold", "jvm gc pause threshold")
                .field("jvm.gc.name", "G1 Young Generation")
                .field("jvm.gc.pause.ms", 250)
                .field("spring.profiles.active", "prod")
                .field("server.port", 8080)
                .field("db.mybatis.statement", "ExampleMapper.select")
                .field("messaging.kafka.consumer.lag", 1234)
                .field("peer.service", "profiles")
                .field(RuntimeLogFields.THRESHOLD_MS, 200)
                .build());

        String eventLine = findJsonEventLine(output, "com.nowcoder.community.runtime");
        JsonNode event = readJson(eventLine);
        assertThat(event.path("service.name").asText()).isEqualTo("community-app");
        assertThat(event.path("service.version").asText()).isEqualTo(SERVICE_VERSION);
        assertThat(event.path("service.namespace").asText()).isEqualTo("community");
        assertThat(event.path("deployment.environment").asText()).isEqualTo("test");
        assertThat(event.path("trace.id").asText()).isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertThat(event.path("span.id").asText()).isEqualTo("bbbbbbbbbbbbbbbb");
        assertThat(eventLine).doesNotContain("\"traceId\"");
        assertThat(eventLine).containsOnlyOnce("\"trace.id\"");
        assertThat(event.path(RuntimeLogFields.EVENT_CATEGORY).asText()).isEqualTo("runtime");
        assertThat(event.path(RuntimeLogFields.EVENT_ACTION).asText()).isEqualTo("jvm_gc_pause_threshold");
        assertThat(event.path(RuntimeLogFields.EVENT_OUTCOME).asText()).isEqualTo("threshold");
        assertThat(event.path(RuntimeLogFields.EVENT_CATEGORY).asText()).isEqualTo("runtime");
        assertThat(event.path(RuntimeLogFields.EVENT_ACTION).asText()).isEqualTo("jvm_gc_pause_threshold");
        assertThat(event.path(RuntimeLogFields.EVENT_OUTCOME).asText()).isEqualTo("threshold");
        assertThat(event.path("jvm.gc.name").asText()).isEqualTo("G1 Young Generation");
        assertThat(event.path("jvm.gc.pause.ms").asText()).isEqualTo("250");
        assertThat(event.path("spring.profiles.active").asText()).isEqualTo("prod");
        assertThat(event.path("server.port").asText()).isEqualTo("8080");
        assertThat(event.path("db.mybatis.statement").asText()).isEqualTo("ExampleMapper.select");
        assertThat(event.path("messaging.kafka.consumer.lag").asText()).isEqualTo("1234");
        assertThat(event.path("peer.service").asText()).isEqualTo("profiles");
        assertThat(event.path(RuntimeLogFields.THRESHOLD_MS).asText()).isEqualTo("200");
        assertThat(event.path("message").asText())
                .contains("jvm gc pause threshold")
                .doesNotContain("community.category=");
    }

    private void initializeProductionLogging(String serviceName) {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.application.name", serviceName);
        environment.setProperty("community.logging.service-version", SERVICE_VERSION);
        environment.setProperty("community.logging.deployment-environment", "test");
        environment.setProperty("spring.profiles.active", "prod");

        loggingSystem.cleanUp();
        loggingSystem.beforeInitialize();
        loggingSystem.initialize(new LoggingInitializationContext(environment), "classpath:logback-spring.xml", null);
    }

    private JsonNode findJsonEvent(CapturedOutput output, String loggerName) {
        return readJson(findJsonEventLine(output, loggerName));
    }

    private String findJsonEventLine(CapturedOutput output, String loggerName) {
        return Arrays.stream(output.getAll().split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isEmpty() && line.startsWith("{"))
                .filter(line -> {
                    JsonNode event = readJson(line);
                    return event != null && loggerName.equals(event.path("logger").asText());
                })
                .findFirst()
                .orElseThrow(() -> new AssertionError("No structured log event found for " + loggerName + " in output: " + output.getAll()));
    }

    private JsonNode readJson(String line) {
        try {
            return objectMapper.readTree(line);
        } catch (IOException ex) {
            return null;
        }
    }
}
