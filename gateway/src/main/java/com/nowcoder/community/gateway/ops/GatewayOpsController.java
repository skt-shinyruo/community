package com.nowcoder.community.gateway.ops;

import com.nowcoder.community.common.api.CommonErrorCode;
import com.nowcoder.community.common.api.GatewayErrorCode;
import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.api.SearchErrorCode;
import com.nowcoder.community.common.internal.dto.OutboxHealthResponse;
import com.nowcoder.community.common.trace.TraceContext;
import com.nowcoder.community.common.trace.TraceId;
import com.nowcoder.community.content.api.rpc.ContentLikeOpsRpcService;
import com.nowcoder.community.content.api.rpc.ContentOutboxRpcService;
import com.nowcoder.community.content.api.rpc.dto.ContentLikeBackfillResponse;
import com.nowcoder.community.gateway.filter.TraceIdSupport;
import com.nowcoder.community.search.api.rpc.SearchOpsRpcService;
import com.nowcoder.community.search.api.rpc.dto.SearchReindexResponse;
import com.nowcoder.community.social.api.rpc.SocialOutboxRpcService;
import com.nowcoder.community.user.api.rpc.UserOutboxRpcService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.rpc.RpcException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.function.Supplier;

/**
 * gateway 内部运维入口（仅允许通过 Spring Cloud Gateway route forward 访问）：
 * - 统一对外路径：/api/ops/**
 * - 服务间调用：Dubbo
 *
 * <p>说明：这些 handler 会触发阻塞式 Dubbo 调用，因此必须 offload 到 boundedElastic。</p>
 */
@RestController
@RequestMapping("/__gateway/ops")
public class GatewayOpsController {

    private static final String HEADER_GATEWAY_FORWARDED = "X-Gateway-Forwarded";
    private static final String REQUIRED_GATEWAY_FORWARDED = "1";

    @DubboReference(check = false, retries = 0, timeout = 600_000)
    private SearchOpsRpcService searchOpsRpcService;

    @DubboReference(check = false, retries = 0, timeout = 30_000)
    private ContentOutboxRpcService contentOutboxRpcService;

    @DubboReference(check = false, retries = 0, timeout = 30_000)
    private SocialOutboxRpcService socialOutboxRpcService;

    @DubboReference(check = false, retries = 0, timeout = 30_000)
    private UserOutboxRpcService userOutboxRpcService;

    @DubboReference(check = false, retries = 0, timeout = 120_000)
    private ContentLikeOpsRpcService contentLikeOpsRpcService;

    @PostMapping("/search/reindex")
    public Mono<ResponseEntity<Result<SearchReindexResponse>>> reindex(
            @RequestHeader(value = TraceIdSupport.HEADER_TRACE_ID, required = false) String traceId,
            @RequestHeader(value = HEADER_GATEWAY_FORWARDED, required = false) String gatewayForwarded
    ) {
        if (!isForwarded(gatewayForwarded)) {
            return Mono.just(notFound(traceId));
        }
        return invoke(traceId, () -> searchOpsRpcService.reindex());
    }

    @GetMapping("/content/outbox/health")
    public Mono<ResponseEntity<Result<OutboxHealthResponse>>> contentOutboxHealth(
            @RequestHeader(value = TraceIdSupport.HEADER_TRACE_ID, required = false) String traceId,
            @RequestHeader(value = HEADER_GATEWAY_FORWARDED, required = false) String gatewayForwarded
    ) {
        if (!isForwarded(gatewayForwarded)) {
            return Mono.just(notFound(traceId));
        }
        return invoke(traceId, () -> contentOutboxRpcService.health());
    }

    @PostMapping("/content/outbox/replay")
    public Mono<ResponseEntity<Result<Integer>>> contentOutboxReplay(
            @RequestHeader(value = TraceIdSupport.HEADER_TRACE_ID, required = false) String traceId,
            @RequestHeader(value = HEADER_GATEWAY_FORWARDED, required = false) String gatewayForwarded,
            @RequestParam(required = false) Integer limit
    ) {
        if (!isForwarded(gatewayForwarded)) {
            return Mono.just(notFound(traceId));
        }
        return invoke(traceId, () -> contentOutboxRpcService.replayFailed(limit));
    }

    @GetMapping("/social/outbox/health")
    public Mono<ResponseEntity<Result<OutboxHealthResponse>>> socialOutboxHealth(
            @RequestHeader(value = TraceIdSupport.HEADER_TRACE_ID, required = false) String traceId,
            @RequestHeader(value = HEADER_GATEWAY_FORWARDED, required = false) String gatewayForwarded
    ) {
        if (!isForwarded(gatewayForwarded)) {
            return Mono.just(notFound(traceId));
        }
        return invoke(traceId, () -> socialOutboxRpcService.health());
    }

    @PostMapping("/social/outbox/replay")
    public Mono<ResponseEntity<Result<Integer>>> socialOutboxReplay(
            @RequestHeader(value = TraceIdSupport.HEADER_TRACE_ID, required = false) String traceId,
            @RequestHeader(value = HEADER_GATEWAY_FORWARDED, required = false) String gatewayForwarded,
            @RequestParam(required = false) Integer limit
    ) {
        if (!isForwarded(gatewayForwarded)) {
            return Mono.just(notFound(traceId));
        }
        return invoke(traceId, () -> socialOutboxRpcService.replayFailed(limit));
    }

    @GetMapping("/user/outbox/health")
    public Mono<ResponseEntity<Result<OutboxHealthResponse>>> userOutboxHealth(
            @RequestHeader(value = TraceIdSupport.HEADER_TRACE_ID, required = false) String traceId,
            @RequestHeader(value = HEADER_GATEWAY_FORWARDED, required = false) String gatewayForwarded
    ) {
        if (!isForwarded(gatewayForwarded)) {
            return Mono.just(notFound(traceId));
        }
        return invoke(traceId, () -> userOutboxRpcService.health());
    }

    @PostMapping("/user/outbox/replay")
    public Mono<ResponseEntity<Result<Integer>>> userOutboxReplay(
            @RequestHeader(value = TraceIdSupport.HEADER_TRACE_ID, required = false) String traceId,
            @RequestHeader(value = HEADER_GATEWAY_FORWARDED, required = false) String gatewayForwarded,
            @RequestParam(required = false) Integer limit
    ) {
        if (!isForwarded(gatewayForwarded)) {
            return Mono.just(notFound(traceId));
        }
        return invoke(traceId, () -> userOutboxRpcService.replayFailed(limit));
    }

    @PostMapping("/content/likes/backfill")
    public Mono<ResponseEntity<Result<ContentLikeBackfillResponse>>> contentLikesBackfill(
            @RequestHeader(value = TraceIdSupport.HEADER_TRACE_ID, required = false) String traceId,
            @RequestHeader(value = HEADER_GATEWAY_FORWARDED, required = false) String gatewayForwarded,
            @RequestParam int entityType,
            @RequestParam(required = false) Long maxItems,
            @RequestParam(required = false) Integer batchSize
    ) {
        if (!isForwarded(gatewayForwarded)) {
            return Mono.just(notFound(traceId));
        }
        return invoke(traceId, () -> contentLikeOpsRpcService.backfill(entityType, maxItems, batchSize));
    }

    private boolean isForwarded(String gatewayForwarded) {
        return REQUIRED_GATEWAY_FORWARDED.equals(gatewayForwarded);
    }

    private <T> Mono<ResponseEntity<Result<T>>> invoke(String traceIdHeader, Supplier<Result<T>> call) {
        String traceId = TraceIdSupport.normalizeTraceId(traceIdHeader);
        if (!StringUtils.hasText(traceId)) {
            traceId = TraceIdSupport.generateTraceId();
        }
        String finalTraceId = traceId;
        return Mono.fromCallable(() -> callWithTrace(finalTraceId, call))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private <T> ResponseEntity<Result<T>> callWithTrace(String traceId, Supplier<Result<T>> call) {
        String before = TraceId.get();
        TraceContext.clear();
        TraceContext.set(traceId);

        try {
            Result<T> result = call == null ? null : call.get();
            if (result == null) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Result.error(GatewayErrorCode.UPSTREAM_UNAVAILABLE));
            }
            HttpStatus status = httpStatusOf(result);
            return ResponseEntity.status(status).body(result);
        } catch (RpcException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Result.error(GatewayErrorCode.UPSTREAM_UNAVAILABLE));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.error(GatewayErrorCode.INTERNAL_ERROR));
        } finally {
            TraceContext.clear();
            if (StringUtils.hasText(before)) {
                TraceContext.set(before);
            }
        }
    }

    private <T> ResponseEntity<Result<T>> notFound(String traceIdHeader) {
        Result<T> body = Result.error(CommonErrorCode.NOT_FOUND);
        String traceId = TraceIdSupport.normalizeTraceId(traceIdHeader);
        if (StringUtils.hasText(traceId)) {
            body.setTraceId(traceId);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    private HttpStatus httpStatusOf(Result<?> result) {
        if (result == null) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        int code = result.getCode();
        if (code == 0) {
            return HttpStatus.OK;
        }
        if (code >= 400 && code < 600) {
            try {
                return HttpStatus.valueOf(code);
            } catch (IllegalArgumentException ignored) {
                return HttpStatus.INTERNAL_SERVER_ERROR;
            }
        }
        SearchErrorCode searchError = searchErrorByCode(code);
        if (searchError != null) {
            try {
                return HttpStatus.valueOf(searchError.getHttpStatus());
            } catch (IllegalArgumentException ignored) {
                return HttpStatus.INTERNAL_SERVER_ERROR;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private SearchErrorCode searchErrorByCode(int code) {
        for (SearchErrorCode e : SearchErrorCode.values()) {
            if (e.getCode() == code) {
                return e;
            }
        }
        return null;
    }
}

