package com.nowcoder.community.infra.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.infra.trace.TraceContext;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class AuditLogFilterTest {

    private static final String SERVICE_VERSION = "test-service-version";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LoggingSystem loggingSystem = LoggingSystem.get(getClass().getClassLoader());

    @AfterEach
    void tearDown() {
        TraceContext.clear();
        SecurityContextHolder.clearContext();
        loggingSystem.cleanUp();
    }

    @ParameterizedTest
    @CsvSource({
            "201, success",
            "403, denied",
            "500, failure"
    })
    void writeRequestShouldEmitStructuredAuditTaxonomy(int status, String outcome, CapturedOutput output)
            throws ServletException, IOException {
        initializeJsonLogs("community-app");
        TraceContext.set("audit-trace-id");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("42", null, "ROLE_USER"));

        AuditLogFilter filter = new AuditLogFilter("community-app");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/posts");
        request.setContentType("application/json");
        request.setContent("{\"title\":\"hello\",\"password\":\"secret\"}".getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(status);

        filter.doFilter(request, response, new MockFilterChain());

        JsonNode event = findJsonEvent(output, AuditLogFilter.class.getName());
        assertThat(event.path("service.name").asText()).isEqualTo("community-app");
        assertThat(event.path("service.version").asText()).isEqualTo(SERVICE_VERSION);
        assertThat(event.path("trace.id").asText()).isEqualTo("audit-trace-id");
        assertThat(event.path("community.category").asText()).isEqualTo("audit");
        assertThat(event.path("community.action").asText()).isEqualTo("http_write_request");
        assertThat(event.path("community.outcome").asText()).isEqualTo(outcome);
        assertThat(event.path("level").asText()).isEqualTo("INFO");
        assertThat(event.path("message").asText())
                .contains("[audit][app=community-app]")
                .contains("method=POST")
                .contains("path=/api/posts")
                .contains("status=" + status)
                .contains("userId=42")
                .contains("traceId=audit-trace-id")
                .doesNotContain("community.category=")
                .doesNotContain("community.action=")
                .doesNotContain("community.outcome=")
                .doesNotContain("password")
                .doesNotContain("secret");
    }

    @Test
    void loginPathShouldRemainExcludedFromAuditStream(CapturedOutput output) throws ServletException, IOException {
        initializeJsonLogs("community-app");

        AuditLogFilter filter = new AuditLogFilter("community-app");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(output.getAll()).doesNotContain(AuditLogFilter.class.getName());
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
}
