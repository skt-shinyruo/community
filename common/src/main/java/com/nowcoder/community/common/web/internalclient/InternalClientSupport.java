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
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClientException;
import org.springframework.util.StringUtils;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public final class InternalClientSupport {

    public static final String OUTCOME_SUCCESS = "success";
    public static final String OUTCOME_ERROR = "error";
    public static final String OUTCOME_TIMEOUT = "timeout";
    public static final String OUTCOME_DEGRADED = "degraded";
    public static final String OUTCOME_FORBIDDEN = "forbidden";

    private InternalClientSupport() {
    }

    public static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(MediaType.parseMediaTypes(MediaType.APPLICATION_JSON_VALUE));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /**
     * RestTemplate 默认会在 4xx/5xx 时抛异常，导致调用方拿不到统一的 Result 错误体。
     * internal client 场景下，我们需要“非 2xx 也读取 body”，再由 unwrap 做语义保真与异常映射。
     */
    public static ResponseErrorHandler passThroughResponseErrorHandler() {
        return new ResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) throws IOException {
                return false;
            }

            @Override
            public void handleError(ClientHttpResponse response) throws IOException {
                // no-op
            }
        };
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
            int httpStatus = result.getHttpStatus();
            if (httpStatus <= 0) {
                httpStatus = code >= 400 && code < 600 ? code : 500;
            }
            throw new BusinessException(new SimpleErrorCode(code, msg, httpStatus), detail);
        }
        return result.getData();
    }

    public static <T> T unwrap(ResponseEntity<Result<T>> response, String serviceName) {
        if (response == null) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, serviceName + " 响应为空");
        }
        Result<T> result = response.getBody();
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
            int httpStatus = response.getStatusCode() == null ? 500 : response.getStatusCode().value();
            throw new BusinessException(new SimpleErrorCode(code, msg, httpStatus), detail);
        }
        return result.getData();
    }

    public static boolean isTimeout(Throwable t) {
        return t instanceof RestClientException && String.valueOf(t.getMessage()).toLowerCase().contains("timed out");
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
