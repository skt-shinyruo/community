package com.nowcoder.community.gateway.edge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.trace.TraceHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class AccessLogWebFilterTest {

    private static final String SERVICE_VERSION = "test-service-version";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LoggingSystem loggingSystem = LoggingSystem.get(getClass().getClassLoader());

    @AfterEach
    void tearDown() {
        MDC.remove("traceId");
        loggingSystem.cleanUp();
    }

    @Test
    void accessLogShouldUseResolvedTraceIdOnlyAroundLogEmission(CapturedOutput output) {
        initializeJsonLogs("community-gateway");

        String staleTraceId = "stale-mdc-trace";
        String resolvedTraceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        String legacyTraceId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        MDC.put("traceId", staleTraceId);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/posts")
                .header(TraceHeaders.HEADER_TRACE_ID, legacyTraceId)
                .header(TraceHeaders.HEADER_TRACEPARENT, traceparent(resolvedTraceId))
                .build());

        com.nowcoder.community.common.webflux.TraceIdWebFilter traceIdWebFilter =
                new com.nowcoder.community.common.webflux.TraceIdWebFilter();
        AccessLogWebFilter accessLogWebFilter = new AccessLogWebFilter();

        traceIdWebFilter.filter(exchange, current -> accessLogWebFilter.filter(current, tracedExchange -> {
            assertThat(MDC.get("traceId")).isEqualTo(staleTraceId);
            tracedExchange.getResponse().setStatusCode(HttpStatus.OK);
            return Mono.empty();
        })).block();

        JsonNode event = findJsonEvent(output, AccessLogWebFilter.class.getName());

        assertThat(event.path("service.name").asText()).isEqualTo("community-gateway");
        assertThat(event.path("service.version").asText()).isEqualTo(SERVICE_VERSION);
        assertThat(event.path("trace.id").asText()).isEqualTo(resolvedTraceId);
        assertThat(event.path("level").asText()).isEqualTo("INFO");
        assertThat(event.path("logger").asText()).isEqualTo(AccessLogWebFilter.class.getName());
        assertThat(event.path("community.category").asText()).isEqualTo("access");
        assertThat(event.path("community.action").asText()).isEqualTo("gateway_http_access");
        assertThat(event.path("community.outcome").asText()).isEqualTo("success");
        assertThat(event.path("message").asText())
                .contains("method=GET")
                .contains("path=/api/posts")
                .contains("status=200")
                .contains("traceId=" + resolvedTraceId);
        assertThat(MDC.get("traceId")).isEqualTo(staleTraceId);
    }

    @Test
    void gatewayFiltersShouldDeclareExplicitOrdering() {
        com.nowcoder.community.common.webflux.TraceIdWebFilter traceIdWebFilter =
                new com.nowcoder.community.common.webflux.TraceIdWebFilter();
        AccessLogWebFilter accessLogWebFilter = new AccessLogWebFilter();

        assertThat(traceIdWebFilter).isInstanceOf(Ordered.class);
        assertThat(accessLogWebFilter).isInstanceOf(Ordered.class);

        Ordered traceOrdered = (Ordered) traceIdWebFilter;
        Ordered accessOrdered = (Ordered) accessLogWebFilter;
        assertThat(traceOrdered.getOrder()).isLessThan(accessOrdered.getOrder());
    }

    private void initializeJsonLogs(String serviceName) {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.application.name", serviceName);
        environment.setProperty("community.logging.service-version", SERVICE_VERSION);
        environment.setProperty("spring.profiles.active", "dev,json-logs");

        loggingSystem.cleanUp();
        loggingSystem.beforeInitialize();
        loggingSystem.initialize(new LoggingInitializationContext(environment), "classpath:logback-spring.xml", null);
    }

    private JsonNode findJsonEvent(CapturedOutput output, String loggerName) {
        return Arrays.stream(output.getAll().split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isEmpty() && line.startsWith("{"))
                .map(this::readJson)
                .filter(event -> event != null && loggerName.equals(event.path("logger").asText()))
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

    private static String traceparent(String traceId) {
        return "00-" + traceId + "-00f067aa0ba902b7-01";
    }
}
