package com.nowcoder.community.ops.api;

import com.nowcoder.community.contracts.api.CommonErrorCode;
import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.infra.internalclient.InternalClientSupport;
import com.nowcoder.community.search.api.rpc.SearchOpsRpcService;
import com.nowcoder.community.search.api.rpc.dto.SearchReindexResponse;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    private static final String TARGET_SEARCH = "search-service";

    private final MeterRegistry meterRegistry;
    private final SearchOpsRpcService searchOpsRpcService;

    public OpsController(MeterRegistry meterRegistry, SearchOpsRpcService searchOpsRpcService) {
        this.meterRegistry = meterRegistry;
        this.searchOpsRpcService = searchOpsRpcService;
    }

    @PostMapping("/search/reindex")
    public ResponseEntity<Result<SearchReindexResponse>> reindex() {
        return invoke(TARGET_SEARCH, "reindex", () -> searchOpsRpcService.reindex());
    }

    private <T> ResponseEntity<Result<T>> invoke(String target, String api, java.util.function.Supplier<Result<T>> call) {
        long start = System.nanoTime();
        try {
            Result<T> result = call == null ? null : call.get();
            if (result == null) {
                InternalClientSupport.record(meterRegistry, target, api, InternalClientSupport.OUTCOME_UNAVAILABLE, start);
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Result.error(CommonErrorCode.SERVICE_UNAVAILABLE));
            }
            HttpStatus status = httpStatusOf(result);
            InternalClientSupport.record(meterRegistry, target, api, outcomeOf(result), start);
            return ResponseEntity.status(status).body(result);
        } catch (RuntimeException e) {
            boolean unavailable = InternalClientSupport.isTimeout(e) || InternalClientSupport.isConnectionError(e);
            String outcome = unavailable ? InternalClientSupport.OUTCOME_UNAVAILABLE : InternalClientSupport.OUTCOME_ERROR;
            InternalClientSupport.record(meterRegistry, target, api, outcome, start);
            log.warn("[internal-call] target={} api={} outcome={}", target, api, outcome, e);
            if (unavailable) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Result.error(CommonErrorCode.SERVICE_UNAVAILABLE));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.error(CommonErrorCode.INTERNAL_ERROR));
        }
    }

    private String outcomeOf(Result<?> result) {
        if (result == null) {
            return InternalClientSupport.OUTCOME_UNAVAILABLE;
        }
        if (result.getCode() == CommonErrorCode.OK.getCode()) {
            return InternalClientSupport.OUTCOME_SUCCESS;
        }
        int httpStatus = result.getHttpStatus();
        if (httpStatus <= 0) {
            int code = result.getCode();
            httpStatus = code >= 400 && code < 600 ? code : 500;
        }
        if (httpStatus == CommonErrorCode.FORBIDDEN.getHttpStatus()) {
            return InternalClientSupport.OUTCOME_FORBIDDEN;
        }
        if (httpStatus == CommonErrorCode.SERVICE_UNAVAILABLE.getHttpStatus()) {
            return InternalClientSupport.OUTCOME_UNAVAILABLE;
        }
        if (httpStatus == 504) {
            return InternalClientSupport.OUTCOME_TIMEOUT;
        }
        return InternalClientSupport.OUTCOME_REMOTE_ERROR;
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

}

