package com.nowcoder.community.common.web;

import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.trace.TraceContext;
import com.nowcoder.community.common.trace.TraceId;
import jakarta.validation.ConstraintViolationException;
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
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    private static final String SERVICE_VERSION = "test-service-version";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final ResultTraceIdAdvice advice = new ResultTraceIdAdvice();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LoggingSystem loggingSystem = LoggingSystem.get(getClass().getClassLoader());

    @AfterEach
    void tearDown() {
        TraceContext.clear();
        loggingSystem.cleanUp();
    }

    @Test
    void businessExceptionShouldKeepHttpStatusCodeAndTraceId() {
        TraceId.set("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        ResponseEntity<Result<Void>> resp = handler.handleBusiness(new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "bad"));
        advice.beforeBodyWrite(resp.getBody(), null, null, null, null, null);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(400);
        assertThat(resp.getBody().getMessage()).isEqualTo("bad");
        assertThat(resp.getBody().getTraceId()).isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    }

    @Test
    void serverErrorBusinessExceptionShouldBeLoggedWithExceptionTaxonomy(CapturedOutput output) {
        initializeProductionLogging("community-app");
        TraceContext.set("11111111111111111111111111111111", "1111111111111111");
        TraceId.set("22222222222222222222222222222222");

        ResponseEntity<Result<Void>> response = handler.handleBusiness(new BusinessException(CommonErrorCode.INTERNAL_ERROR, "server exploded"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(CommonErrorCode.INTERNAL_ERROR.getCode());

        JsonNode event = findJsonEvent(output, GlobalExceptionHandler.class.getName());
        assertThat(event.path("service.name").asText()).isEqualTo("community-app");
        assertThat(event.path("service.version").asText()).isEqualTo(SERVICE_VERSION);
        assertThat(event.path("service.namespace").asText()).isEqualTo("community");
        assertThat(event.path("deployment.environment").asText()).isEqualTo("test");
        assertThat(event.path("trace.id").asText()).isEqualTo("11111111111111111111111111111111");
        assertThat(event.path("span.id").asText()).isEqualTo("1111111111111111");
        assertThat(event.has("traceId")).isFalse();
        assertThat(event.path("event.category").asText()).isEqualTo("exception");
        assertThat(event.path("event.action").asText()).isEqualTo("business_exception");
        assertThat(event.path("event.outcome").asText()).isEqualTo("failure");
        assertThat(event.path("level").asText()).isEqualTo("ERROR");
        assertThat(event.path("message").asText())
                .contains("[exception][business]")
                .contains("traceId=22222222222222222222222222222222")
                .contains("code=500")
                .contains("status=500")
                .contains("message=server exploded")
                .doesNotContain("community.category=")
                .doesNotContain("community.action=")
                .doesNotContain("community.outcome=");
    }

    @Test
    void validationExceptionShouldBeInvalidArgumentWithTraceId() {
        TraceId.set("cccccccccccccccccccccccccccccccc");
        ResponseEntity<Result<Void>> resp = handler.handleValidation(new ConstraintViolationException("x", Set.of()));
        advice.beforeBodyWrite(resp.getBody(), null, null, null, null, null);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(CommonErrorCode.INVALID_ARGUMENT.getCode());
        assertThat(resp.getBody().getTraceId()).isEqualTo("cccccccccccccccccccccccccccccccc");
    }

    @Test
    void unknownExceptionShouldBeInternalErrorWithTraceId() {
        TraceId.set("dddddddddddddddddddddddddddddddd");
        RuntimeException ex = new RuntimeException("boom") {
            @Override
            public synchronized Throwable fillInStackTrace() {
                return this;
            }
        };
        ResponseEntity<Result<Void>> resp = handler.handleGeneric(ex);
        advice.beforeBodyWrite(resp.getBody(), null, null, null, null, null);

        assertThat(resp.getStatusCode().value()).isEqualTo(500);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(CommonErrorCode.INTERNAL_ERROR.getCode());
        assertThat(resp.getBody().getTraceId()).isEqualTo("dddddddddddddddddddddddddddddddd");
    }

    @Test
    void dataAccessExceptionShouldBeServiceUnavailableAndLogged(CapturedOutput output) {
        initializeProductionLogging("community-app");
        TraceContext.set("33333333333333333333333333333333", "3333333333333333");
        TraceId.set("eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");
        DataAccessException ex = new DataAccessException("db down") {
            @Override
            public synchronized Throwable fillInStackTrace() {
                return this;
            }
        };
        ResponseEntity<Result<Void>> resp = handler.handleDataAccess(ex);
        advice.beforeBodyWrite(resp.getBody(), null, null, null, null, null);

        assertThat(resp.getStatusCode().value()).isEqualTo(503);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE.getCode());
        assertThat(resp.getBody().getTraceId()).isEqualTo("eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");

        JsonNode event = findJsonEvent(output, GlobalExceptionHandler.class.getName());
        assertThat(event.path("service.name").asText()).isEqualTo("community-app");
        assertThat(event.path("service.version").asText()).isEqualTo(SERVICE_VERSION);
        assertThat(event.path("service.namespace").asText()).isEqualTo("community");
        assertThat(event.path("deployment.environment").asText()).isEqualTo("test");
        assertThat(event.path("trace.id").asText()).isEqualTo("33333333333333333333333333333333");
        assertThat(event.path("span.id").asText()).isEqualTo("3333333333333333");
        assertThat(event.has("traceId")).isFalse();
        assertThat(event.path("event.category").asText()).isEqualTo("exception");
        assertThat(event.path("event.action").asText()).isEqualTo("data_access_exception");
        assertThat(event.path("event.outcome").asText()).isEqualTo("failure");
        assertThat(event.path("level").asText()).isEqualTo("ERROR");
        assertThat(event.path("message").asText())
                .contains("[exception][data-access] traceId=eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee")
                .doesNotContain("community.category=")
                .doesNotContain("community.action=")
                .doesNotContain("community.outcome=");
    }

    @Test
    void unknownExceptionShouldBeLogged(CapturedOutput output) {
        initializeProductionLogging("community-app");
        TraceContext.set("44444444444444444444444444444444", "4444444444444444");
        TraceId.set("55555555555555555555555555555555");
        RuntimeException ex = new RuntimeException("boom-2") {
            @Override
            public synchronized Throwable fillInStackTrace() {
                return this;
            }
        };
        handler.handleGeneric(ex);

        JsonNode event = findJsonEvent(output, GlobalExceptionHandler.class.getName());

        assertThat(event.path("service.name").asText()).isEqualTo("community-app");
        assertThat(event.path("service.version").asText()).isEqualTo(SERVICE_VERSION);
        assertThat(event.path("service.namespace").asText()).isEqualTo("community");
        assertThat(event.path("deployment.environment").asText()).isEqualTo("test");
        assertThat(event.path("trace.id").asText()).isEqualTo("44444444444444444444444444444444");
        assertThat(event.path("span.id").asText()).isEqualTo("4444444444444444");
        assertThat(event.has("traceId")).isFalse();
        assertThat(event.path("event.category").asText()).isEqualTo("exception");
        assertThat(event.path("event.action").asText()).isEqualTo("unhandled_exception");
        assertThat(event.path("event.outcome").asText()).isEqualTo("failure");
        assertThat(event.path("level").asText()).isEqualTo("ERROR");
        assertThat(event.path("logger").asText()).isEqualTo(GlobalExceptionHandler.class.getName());
        assertThat(event.path("message").asText())
                .contains("[exception][unhandled] traceId=55555555555555555555555555555555")
                .doesNotContain("community.category=")
                .doesNotContain("community.action=")
                .doesNotContain("community.outcome=");
    }

    @Test
    void missingRequestParamShouldBe400WithTraceId() {
        TraceId.set("ffffffffffffffffffffffffffffffff");
        MissingServletRequestParameterException ex = new MissingServletRequestParameterException("ip", "String");
        ResponseEntity<Result<Void>> resp = handler.handleRequestParam(ex);
        advice.beforeBodyWrite(resp.getBody(), null, null, null, null, null);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(400);
        assertThat(resp.getBody().getTraceId()).isEqualTo("ffffffffffffffffffffffffffffffff");
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
