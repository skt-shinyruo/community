package com.nowcoder.community.common.web.internalclient;

// 内部 HTTP 客户端通用支持：统一 headers、错误映射与指标记录。
import com.nowcoder.community.common.api.CommonErrorCode;
import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.api.SimpleErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

public final class InternalClientSupport {

    public static final String HEADER_INTERNAL_TOKEN = "X-Internal-Token";

    public static final String OUTCOME_SUCCESS = "success";
    public static final String OUTCOME_ERROR = "error";
    public static final String OUTCOME_TIMEOUT = "timeout";
    public static final String OUTCOME_DEGRADED = "degraded";
    public static final String OUTCOME_FORBIDDEN = "forbidden";

    private InternalClientSupport() {
    }

    public static HttpHeaders jsonHeaders(String internalToken, String serviceName) {
        if (!StringUtils.hasText(internalToken)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, serviceName + " internal-token 未配置");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(MediaType.parseMediaTypes(MediaType.APPLICATION_JSON_VALUE));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HEADER_INTERNAL_TOKEN, internalToken);
        return headers;
    }

    public static <T> T unwrap(Result<T> result, String serviceName) {
        if (result == null) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, serviceName + " 响应为空");
        }
        int code = result.getCode();
        if (code != CommonErrorCode.OK.getCode()) {
            String msg = result.getMessage();
            String traceId = result.getTraceId();
            String detail = serviceName + " 返回错误：" + (StringUtils.hasText(msg) ? msg : "unknown");
            if (StringUtils.hasText(traceId)) {
                detail += " (traceId=" + traceId + ")";
            }
            throw new BusinessException(new SimpleErrorCode(code, msg), detail);
        }
        return result.getData();
    }

    public static void record(MeterRegistry meterRegistry, String client, String api, String outcome, long startNanos) {
        if (meterRegistry == null) {
            return;
        }
        Tags tags = Tags.of("client", client, "api", api, "outcome", outcome);
        meterRegistry.counter("internal_client_requests_total", tags).increment();
        meterRegistry.timer("internal_client_latency", tags).record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }
}
