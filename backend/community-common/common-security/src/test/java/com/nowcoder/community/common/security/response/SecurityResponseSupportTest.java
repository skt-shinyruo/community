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
        assertThat(headers.get(TraceHeaders.HEADER_TRACE_ID)).isEqualTo(normalized);
        assertThat(headers.get(TraceHeaders.HEADER_TRACEPARENT))
                .matches("^00-" + normalized + "-[0-9a-f]{16}-01$");
    }
}
