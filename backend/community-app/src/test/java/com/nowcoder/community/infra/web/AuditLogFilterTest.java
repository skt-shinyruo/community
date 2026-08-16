package com.nowcoder.community.common.web;

import com.nowcoder.community.common.trace.TraceContext;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class AuditLogFilterTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @CsvSource({
            "201, success",
            "403, denied",
            "500, failure"
    })
    void writeRequestShouldEmitBusinessAuditLog(int status, String outcome, CapturedOutput output)
            throws ServletException, IOException {
        TraceContext.set("11111111111111111111111111111111", "2222222222222222");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("42", null, "ROLE_USER"));

        AuditLogFilter filter = new AuditLogFilter("community-app");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/posts");
        request.setContentType("application/json");
        request.setContent("{\"title\":\"hello\",\"password\":\"secret\"}".getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(status);

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(output.getAll())
                .contains("[audit][app=community-app]")
                .contains("method=POST")
                .contains("path=/api/posts")
                .contains("status=" + status)
                .contains("outcome=" + outcome)
                .contains("userId=42")
                .contains("traceId=11111111111111111111111111111111")
                .doesNotContain("password")
                .doesNotContain("secret");
    }

    @Test
    void loginPathShouldRemainExcludedFromAuditStream(CapturedOutput output) throws ServletException, IOException {
        AuditLogFilter filter = new AuditLogFilter("community-app");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(output.getAll()).doesNotContain("[audit][app=community-app]");
    }
}
