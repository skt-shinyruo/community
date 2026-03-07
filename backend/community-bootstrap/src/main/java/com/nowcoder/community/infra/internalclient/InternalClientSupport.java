package com.nowcoder.community.infra.internalclient;

// internal client 通用支持：
// - 用于“跨模块内部调用”的统一错误映射与指标记录；
// - 在 A-1 模块化单体下，多数调用是进程内 Spring Bean 调用；
// - 保留该抽象与命名是为了未来可能的拆分（HTTP/RPC）时，调用方无需大改业务逻辑。
import com.nowcoder.community.contracts.api.CommonErrorCode;
import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.contracts.api.SimpleErrorCode;
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
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

public final class InternalClientSupport {

    public static final String OUTCOME_SUCCESS = "success";
    public static final String OUTCOME_ERROR = "error";
    public static final String OUTCOME_TIMEOUT = "timeout";
    public static final String OUTCOME_UNAVAILABLE = "unavailable";
    public static final String OUTCOME_DEGRADED = "degraded";
    public static final String OUTCOME_FORBIDDEN = "forbidden";
    public static final String OUTCOME_REMOTE_ERROR = "remote_error";

    private InternalClientSupport() {
    }

    public static <T> T callResult(MeterRegistry meterRegistry, String target, String api, Supplier<Result<T>> supplier) {
        return callResult(meterRegistry, target, api, supplier, InternalCallOptions.failClosed());
    }

    public static <T> T callResult(
            MeterRegistry meterRegistry,
            String target,
            String api,
            Supplier<Result<T>> supplier,
            InternalCallOptions<T> options
    ) {
        return call(meterRegistry, target, api, () -> unwrap(supplier.get(), target), options);
    }

    /**
     * Unified internal call wrapper for {@code Result<T>} that also allows mapping/validation on the unwrapped data
     * inside the same metrics/error-handling scope.
     *
     * <p>This avoids "unwrap first, then validate/map" patterns that would otherwise record SUCCESS and only later
     * throw validation errors outside the internal-call boundary.</p>
     */
    public static <T, R> R callResultAndThen(
            MeterRegistry meterRegistry,
            String target,
            String api,
            Supplier<Result<T>> supplier,
            Function<T, R> mapper
    ) {
        return callResultAndThen(meterRegistry, target, api, supplier, mapper, InternalCallOptions.failClosed());
    }

    public static <T, R> R callResultAndThen(
            MeterRegistry meterRegistry,
            String target,
            String api,
            Supplier<Result<T>> supplier,
            Function<T, R> mapper,
            InternalCallOptions<R> options
    ) {
        return call(meterRegistry, target, api, () -> mapper.apply(unwrap(supplier.get(), target)), options);
    }

    public static <T> T call(MeterRegistry meterRegistry, String target, String api, Supplier<T> supplier) {
        return call(meterRegistry, target, api, supplier, InternalCallOptions.failClosed());
    }

    public static <T> T call(
            MeterRegistry meterRegistry,
            String target,
            String api,
            Supplier<T> supplier,
            InternalCallOptions<T> options
    ) {
        long start = System.nanoTime();
        try {
            T v = supplier.get();
            record(meterRegistry, target, api, OUTCOME_SUCCESS, start);
            return v;
        } catch (RuntimeException e) {
            InternalCallOptions<T> o = options == null ? InternalCallOptions.failClosed() : options;
            if (o.failOpen() && o.fallback() != null) {
                record(meterRegistry, target, api, OUTCOME_DEGRADED, start);
                warn(o, target, api, OUTCOME_DEGRADED, e);
                return o.fallback().get();
            }
            if (e instanceof BusinessException be) {
                record(meterRegistry, target, api, classifyBusinessOutcome(be), start);
                throw be;
            }
            String outcome = classifyUnexpectedOutcome(e);
            record(meterRegistry, target, api, outcome, start);
            warn(o, target, api, outcome, e);
            throw wrapUnexpectedException(target, e);
        }
    }

    public static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(MediaType.parseMediaTypes(MediaType.APPLICATION_JSON_VALUE));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /**
     * HTTP 场景下的 RestTemplate 支持：
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
        if (t == null) {
            return false;
        }
        if (hasCause(t, SocketTimeoutException.class) || hasCause(t, TimeoutException.class)) {
            return true;
        }
        // JDK HTTP client timeout exception (avoid static reference to keep code simple across JDKs)
        if (hasCauseByName(t, "java.net.http.HttpTimeoutException")) {
            return true;
        }
        // Spring RestClientException (best-effort message-based fallback)
        if (t instanceof RestClientException && containsIgnoreCase(String.valueOf(t.getMessage()), "timed out")) {
            return true;
        }
        return containsIgnoreCase(String.valueOf(t.getMessage()), "timed out")
                || containsIgnoreCase(String.valueOf(t.getMessage()), "timeout");
    }

    public static boolean isConnectionError(Throwable t) {
        if (t == null) {
            return false;
        }
        return hasCause(t, ConnectException.class)
                || hasCause(t, UnknownHostException.class)
                || hasCause(t, NoRouteToHostException.class)
                || hasCause(t, SocketException.class);
    }

    public static BusinessException wrapUnexpectedException(String target, Throwable t) {
        String service = String.valueOf(target);
        if (isTimeout(t) || isConnectionError(t)) {
            return new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, service + " 不可用");
        }
        return new BusinessException(CommonErrorCode.INTERNAL_ERROR, service + " 调用失败");
    }

    public static void record(MeterRegistry meterRegistry, String target, String api, String outcome, long startNanos) {
        if (meterRegistry == null) {
            return;
        }
        Tags tags = Tags.of("target", String.valueOf(target), "api", String.valueOf(api), "outcome", String.valueOf(outcome));
        meterRegistry.counter("internal_call_requests_total", tags).increment();
        meterRegistry.timer("internal_call_latency", tags).record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }

    private static String classifyBusinessOutcome(BusinessException e) {
        if (e == null || e.getErrorCode() == null) {
            return OUTCOME_REMOTE_ERROR;
        }
        int httpStatus = e.getErrorCode().getHttpStatus();
        if (httpStatus == CommonErrorCode.FORBIDDEN.getHttpStatus()) {
            return OUTCOME_FORBIDDEN;
        }
        if (httpStatus == CommonErrorCode.SERVICE_UNAVAILABLE.getHttpStatus()) {
            return OUTCOME_UNAVAILABLE;
        }
        if (httpStatus == 504) {
            return OUTCOME_TIMEOUT;
        }
        return OUTCOME_REMOTE_ERROR;
    }

    private static String classifyUnexpectedOutcome(Throwable t) {
        if (isTimeout(t)) {
            return OUTCOME_TIMEOUT;
        }
        if (isConnectionError(t)) {
            return OUTCOME_UNAVAILABLE;
        }
        return OUTCOME_ERROR;
    }

    private static void warn(InternalCallOptions<?> options, String target, String api, String outcome, Throwable t) {
        if (options == null || options.warnLogger() == null) {
            return;
        }
        String msg = "[internal-call] target=" + String.valueOf(target)
                + " api=" + String.valueOf(api)
                + " outcome=" + String.valueOf(outcome);
        options.warnLogger().accept(msg, t);
    }

    private static boolean containsIgnoreCase(String s, String needle) {
        if (!StringUtils.hasText(s) || !StringUtils.hasText(needle)) {
            return false;
        }
        return s.toLowerCase().contains(needle.toLowerCase());
    }

    private static boolean hasCauseByName(Throwable t, String className) {
        if (!StringUtils.hasText(className)) {
            return false;
        }
        Throwable cur = t;
        Map<Throwable, Boolean> seen = new IdentityHashMap<>();
        while (cur != null && !seen.containsKey(cur)) {
            seen.put(cur, Boolean.TRUE);
            if (className.equals(cur.getClass().getName())) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    private static boolean hasCause(Throwable t, Class<? extends Throwable> type) {
        if (t == null || type == null) {
            return false;
        }
        Throwable cur = t;
        Map<Throwable, Boolean> seen = new IdentityHashMap<>();
        while (cur != null && !seen.containsKey(cur)) {
            seen.put(cur, Boolean.TRUE);
            if (type.isInstance(cur)) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }
}
