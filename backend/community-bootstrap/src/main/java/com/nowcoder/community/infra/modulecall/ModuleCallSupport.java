package com.nowcoder.community.infra.modulecall;

// 模块间调用通用支持：
// - 用于“跨模块内部调用”的统一错误映射与指标记录；
// - 在模块化单体下，多数调用是进程内 Spring Bean 调用；
// - 抽象边界用于约束依赖方向（调用方只依赖对方 api/application/event），而不是为了未来拆分的分布式调用契约。
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

public final class ModuleCallSupport {

    public static final String OUTCOME_SUCCESS = "success";
    public static final String OUTCOME_ERROR = "error";
    public static final String OUTCOME_TIMEOUT = "timeout";
    public static final String OUTCOME_UNAVAILABLE = "unavailable";
    public static final String OUTCOME_DEGRADED = "degraded";
    public static final String OUTCOME_FORBIDDEN = "forbidden";
    public static final String OUTCOME_REMOTE_ERROR = "remote_error";

    private ModuleCallSupport() {
    }

    public static <T> T callResult(MeterRegistry meterRegistry, String target, String api, Supplier<Result<T>> supplier) {
        return callResult(meterRegistry, target, api, supplier, ModuleCallOptions.failClosed());
    }

    public static <T> T callResult(
            MeterRegistry meterRegistry,
            String target,
            String api,
            Supplier<Result<T>> supplier,
            ModuleCallOptions<T> options
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
        return callResultAndThen(meterRegistry, target, api, supplier, mapper, ModuleCallOptions.failClosed());
    }

    public static <T, R> R callResultAndThen(
            MeterRegistry meterRegistry,
            String target,
            String api,
            Supplier<Result<T>> supplier,
            Function<T, R> mapper,
            ModuleCallOptions<R> options
    ) {
        return call(meterRegistry, target, api, () -> mapper.apply(unwrap(supplier.get(), target)), options);
    }

    public static <T> T call(MeterRegistry meterRegistry, String target, String api, Supplier<T> supplier) {
        return call(meterRegistry, target, api, supplier, ModuleCallOptions.failClosed());
    }

    public static <T> T call(
            MeterRegistry meterRegistry,
            String target,
            String api,
            Supplier<T> supplier,
            ModuleCallOptions<T> options
    ) {
        long start = System.nanoTime();
        try {
            T v = supplier.get();
            record(meterRegistry, target, api, OUTCOME_SUCCESS, start);
            return v;
        } catch (RuntimeException e) {
            ModuleCallOptions<T> o = options == null ? ModuleCallOptions.failClosed() : options;
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
     * 模块调用（或受控内部 HTTP 调用）场景下，我们需要“非 2xx 也读取 body”，再由 unwrap 做语义保真与异常映射。
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

    public static <T> T unwrap(Result<T> result, String target) {
        if (result == null) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, String.valueOf(target) + " 响应为空");
        }
        int code = result.getCode();
        if (code != CommonErrorCode.OK.getCode()) {
            String msg = result.getMessage();
            String traceId = result.getTraceId();
            String detail = String.valueOf(target) + " 返回错误：" + (StringUtils.hasText(msg) ? msg : "unknown");
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

    public static <T> T unwrap(ResponseEntity<Result<T>> response, String target) {
        if (response == null) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, String.valueOf(target) + " 响应为空");
        }
        Result<T> result = response.getBody();
        if (result == null) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, String.valueOf(target) + " 响应为空");
        }
        int code = result.getCode();
        if (code != CommonErrorCode.OK.getCode()) {
            String msg = result.getMessage();
            String traceId = result.getTraceId();
            String detail = String.valueOf(target) + " 返回错误：" + (StringUtils.hasText(msg) ? msg : "unknown");
            if (StringUtils.hasText(traceId)) {
                detail += " (traceId=" + traceId + ")";
            }
            int responseStatus = response.getStatusCode() == null ? 0 : response.getStatusCode().value();
            int httpStatus = responseStatus;
            // Some downstreams always respond 200 and use Result.{code,httpStatus} to express errors.
            // In that case we must restore proper HTTP semantics from the body (or derive from code),
            // otherwise BusinessException will carry a misleading 2xx status and break classification.
            if (responseStatus <= 0 || (responseStatus >= 200 && responseStatus < 300)) {
                httpStatus = result.getHttpStatus();
                if (httpStatus <= 0) {
                    httpStatus = code >= 400 && code < 600 ? code : 500;
                }
            }
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
            return new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, service + " 不可用", t);
        }
        return new BusinessException(CommonErrorCode.INTERNAL_ERROR, service + " 调用失败", t);
    }

    public static void record(MeterRegistry meterRegistry, String target, String api, String outcome, long startNanos) {
        if (meterRegistry == null) {
            return;
        }
        Tags tags = Tags.of("module", String.valueOf(target), "api", String.valueOf(api), "outcome", String.valueOf(outcome));
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

    private static void warn(ModuleCallOptions<?> options, String target, String api, String outcome, Throwable t) {
        if (options == null || options.warnLogger() == null) {
            return;
        }
        String msg = "[module-call] module=" + String.valueOf(target)
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
