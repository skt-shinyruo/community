package com.nowcoder.community.common.security.response;

import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.trace.TraceHeaders;
import com.nowcoder.community.common.web.Result;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityResponseSupportTest {

    @Test
    void unauthorized_shouldBackfillTraceIdAndHeaders() {
        String traceId = "ABCDEFABCDEFABCDEFABCDEFABCDEFAB";
        String normalized = traceId.toLowerCase();
        Map<String, String> headers = new LinkedHashMap<>();

        Result<?> body = SecurityResponseSupport.unauthorized(traceId, headers::put);

        assertThat(body.getCode()).isEqualTo(CommonErrorCode.UNAUTHORIZED.getCode());
        assertThat(body.getTraceId()).isEqualTo(normalized);
        assertThat(headers.get(TraceHeaders.HEADER_TRACEPARENT))
                .matches("^00-" + normalized + "-[0-9a-f]{16}-01$");
    }

    @Test
    void forbidden_shouldBackfillTraceIdAndHeaders() {
        String traceId = "ABCDEFABCDEFABCDEFABCDEFABCDEFAB";
        String normalized = traceId.toLowerCase();
        Map<String, String> headers = new LinkedHashMap<>();

        Result<?> body = SecurityResponseSupport.forbidden(traceId, headers::put);

        assertThat(body.getCode()).isEqualTo(CommonErrorCode.FORBIDDEN.getCode());
        assertThat(body.getTraceId()).isEqualTo(normalized);
        assertThat(headers.get(TraceHeaders.HEADER_TRACEPARENT))
                .matches("^00-" + normalized + "-[0-9a-f]{16}-01$");
    }

    @Test
    void resolveTraceId_shouldGenerateWhenCurrentAndTraceparentAreMissing() {
        String resolved = SecurityResponseSupport.resolveTraceId(
                null,
                null
        );

        assertThat(resolved).matches("[0-9a-f]{32}");
    }

    @Test
    void resolveTraceId_shouldUseTraceparentWhenCurrentMissing() {
        String resolved = SecurityResponseSupport.resolveTraceId(
                null,
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
        );

        assertThat(resolved).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    }
}
