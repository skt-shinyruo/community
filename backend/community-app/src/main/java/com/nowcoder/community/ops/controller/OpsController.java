package com.nowcoder.community.ops.controller;

import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.search.dto.SearchReindexResponse;
import com.nowcoder.community.search.service.SearchAdminService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 运维平面 API：
 * - 对外路径：/api/ops/**
 * - 内部调用：进程内 Spring Bean
 *
 * <p>说明：该服务用于隔离高风险/高成本运维能力，降低 edge gateway 的爆炸半径。</p>
 */
@RestController
@RequestMapping("/api/ops")
public class OpsController {

    private static final Logger log = LoggerFactory.getLogger(OpsController.class);
    private static final String INTERNAL_CALL_REQUESTS_TOTAL = "internal_call_requests_total";
    private static final String INTERNAL_CALL_LATENCY = "internal_call_latency";
    private static final String OUTCOME_SUCCESS = "success";
    private static final String OUTCOME_ERROR = "error";
    private static final String OUTCOME_TIMEOUT = "timeout";
    private static final String OUTCOME_UNAVAILABLE = "unavailable";
    private static final String OUTCOME_FORBIDDEN = "forbidden";
    private static final String OUTCOME_REMOTE_ERROR = "remote_error";
    private static final String TARGET_MODULE_SEARCH = "search";

    private final MeterRegistry meterRegistry;
    private final SearchAdminService searchAdminService;

    public OpsController(MeterRegistry meterRegistry, SearchAdminService searchAdminService) {
        this.meterRegistry = meterRegistry;
        this.searchAdminService = searchAdminService;
    }

    @PostMapping("/search/reindex")
    public ResponseEntity<Result<SearchReindexResponse>> reindex() {
        return invoke(TARGET_MODULE_SEARCH, "reindex", () -> Result.ok(searchAdminService.reindex()));
    }

    private <T> ResponseEntity<Result<T>> invoke(String target, String api, java.util.function.Supplier<Result<T>> call) {
        long start = System.nanoTime();
        try {
            Result<T> result = call == null ? null : call.get();
            if (result == null) {
                record(target, api, OUTCOME_UNAVAILABLE, start);
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Result.error(CommonErrorCode.SERVICE_UNAVAILABLE));
            }
            HttpStatus status = httpStatusOf(result);
            record(target, api, outcomeOf(result), start);
            return ResponseEntity.status(status).body(result);
        } catch (RuntimeException e) {
            boolean unavailable = isTimeout(e) || isConnectionError(e);
            String outcome = unavailable ? OUTCOME_UNAVAILABLE : OUTCOME_ERROR;
            record(target, api, outcome, start);
            log.warn("[internal-call] module={} api={} outcome={}", target, api, outcome, e);
            if (unavailable) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Result.error(CommonErrorCode.SERVICE_UNAVAILABLE));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.error(CommonErrorCode.INTERNAL_ERROR));
        }
    }

    private String outcomeOf(Result<?> result) {
        if (result == null) {
            return OUTCOME_UNAVAILABLE;
        }
        if (result.getCode() == CommonErrorCode.OK.getCode()) {
            return OUTCOME_SUCCESS;
        }
        int httpStatus = result.getHttpStatus();
        if (httpStatus <= 0) {
            int code = result.getCode();
            httpStatus = code >= 400 && code < 600 ? code : 500;
        }
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

    private HttpStatus httpStatusOf(Result<?> result) {
        if (result == null) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        int code = result.getCode();
        if (code == CommonErrorCode.OK.getCode()) {
            return HttpStatus.OK;
        }
        int httpStatus = result.getHttpStatus();
        if (httpStatus <= 0) {
            httpStatus = code >= 400 && code < 600 ? code : 500;
        }
        try {
            return HttpStatus.valueOf(httpStatus);
        } catch (IllegalArgumentException ignored) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    private void record(String target, String api, String outcome, long startNanos) {
        if (meterRegistry == null) {
            return;
        }
        Tags tags = Tags.of("module", String.valueOf(target), "api", String.valueOf(api), "outcome", String.valueOf(outcome));
        meterRegistry.counter(INTERNAL_CALL_REQUESTS_TOTAL, tags).increment();
        meterRegistry.timer(INTERNAL_CALL_LATENCY, tags).record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }

    private boolean isTimeout(Throwable t) {
        if (t == null) {
            return false;
        }
        if (hasCause(t, SocketTimeoutException.class) || hasCause(t, TimeoutException.class)) {
            return true;
        }
        if (hasCauseByName(t, "java.net.http.HttpTimeoutException")) {
            return true;
        }
        String message = String.valueOf(t.getMessage());
        return containsIgnoreCase(message, "timed out") || containsIgnoreCase(message, "timeout");
    }

    private boolean isConnectionError(Throwable t) {
        if (t == null) {
            return false;
        }
        return hasCause(t, ConnectException.class)
                || hasCause(t, UnknownHostException.class)
                || hasCause(t, NoRouteToHostException.class)
                || hasCause(t, SocketException.class);
    }

    private boolean containsIgnoreCase(String value, String needle) {
        if (!StringUtils.hasText(value) || !StringUtils.hasText(needle)) {
            return false;
        }
        return value.toLowerCase().contains(needle.toLowerCase());
    }

    private boolean hasCauseByName(Throwable t, String className) {
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

    private boolean hasCause(Throwable t, Class<? extends Throwable> type) {
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
