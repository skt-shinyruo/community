package com.nowcoder.community.im.core.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.common.trace.TraceContext;
import com.nowcoder.community.common.trace.TraceId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    private static final String SERVICE_VERSION = "test-service-version";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LoggingSystem loggingSystem = LoggingSystem.get(getClass().getClassLoader());

    @AfterEach
    void tearDown() {
        TraceContext.clear();
        loggingSystem.cleanUp();
    }

    @Test
    void unknownExceptionShouldKeepTraceIdAndLogStructuredEvent(CapturedOutput output) {
        initializeProductionLogging("im-core");
        TraceContext.set("im-mdc-1");
        TraceId.set("im-thread-1");
        RuntimeException ex = new RuntimeException("boom") {
            @Override
            public synchronized Throwable fillInStackTrace() {
                return this;
            }
        };

        ResponseEntity<Result<Void>> response = handler.handleGeneric(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(CommonErrorCode.INTERNAL_ERROR.getCode());

        JsonNode event = findJsonEvent(output, GlobalExceptionHandler.class.getName());
        assertThat(event.path("service.name").asText()).isEqualTo("im-core");
        assertThat(event.path("service.version").asText()).isEqualTo(SERVICE_VERSION);
        assertThat(event.path("trace.id").asText()).isEqualTo("im-mdc-1");
        assertThat(event.path("community.category").asText()).isEqualTo("exception");
        assertThat(event.path("community.action").asText()).isEqualTo("unhandled_exception");
        assertThat(event.path("community.outcome").asText()).isEqualTo("failure");
        assertThat(event.path("level").asText()).isEqualTo("ERROR");
        assertThat(event.path("logger").asText()).isEqualTo(GlobalExceptionHandler.class.getName());
        assertThat(event.path("message").asText())
                .contains("[im-core][unhandled] traceId=im-thread-1")
                .doesNotContain("community.category=")
                .doesNotContain("community.action=")
                .doesNotContain("community.outcome=");
    }

    @Test
    void dataAccessExceptionShouldLogExceptionTaxonomy(CapturedOutput output) {
        initializeProductionLogging("im-core");
        TraceContext.set("im-mdc-2");
        TraceId.set("im-thread-2");
        DataAccessException ex = new DataAccessException("db gone") {
            @Override
            public synchronized Throwable fillInStackTrace() {
                return this;
            }
        };

        ResponseEntity<Result<Void>> response = handler.handleDataAccess(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE.getCode());

        JsonNode event = findJsonEvent(output, GlobalExceptionHandler.class.getName());
        assertThat(event.path("service.name").asText()).isEqualTo("im-core");
        assertThat(event.path("service.version").asText()).isEqualTo(SERVICE_VERSION);
        assertThat(event.path("trace.id").asText()).isEqualTo("im-mdc-2");
        assertThat(event.path("community.category").asText()).isEqualTo("exception");
        assertThat(event.path("community.action").asText()).isEqualTo("data_access_exception");
        assertThat(event.path("community.outcome").asText()).isEqualTo("failure");
        assertThat(event.path("level").asText()).isEqualTo("ERROR");
        assertThat(event.path("message").asText())
                .contains("[im-core][data-access] traceId=im-thread-2")
                .doesNotContain("community.category=")
                .doesNotContain("community.action=")
                .doesNotContain("community.outcome=");
    }

    private void initializeProductionLogging(String serviceName) {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.application.name", serviceName);
        environment.setProperty("community.logging.service-version", SERVICE_VERSION);
        environment.setProperty("spring.profiles.active", "prod");

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
}
