package com.nowcoder.community.ops.api;

import com.nowcoder.community.common.api.CommonErrorCode;
import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.internal.dto.OutboxHealthResponse;
import com.nowcoder.community.content.api.rpc.ContentLikeOpsRpcService;
import com.nowcoder.community.content.api.rpc.ContentOutboxRpcService;
import com.nowcoder.community.content.api.rpc.dto.ContentLikeBackfillResponse;
import com.nowcoder.community.search.api.rpc.SearchOpsRpcService;
import com.nowcoder.community.search.api.rpc.dto.SearchReindexResponse;
import com.nowcoder.community.social.api.rpc.SocialOutboxRpcService;
import com.nowcoder.community.user.api.rpc.UserOutboxRpcService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.rpc.RpcException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运维平面 API：
 * - 对外路径：/api/ops/**
 * - 内部调用：Dubbo RPC
 *
 * <p>说明：该服务用于隔离高风险/高成本运维能力，降低 edge gateway 的爆炸半径。</p>
 */
@RestController
@RequestMapping("/api/ops")
public class OpsController {

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
    public ResponseEntity<Result<SearchReindexResponse>> reindex() {
        return invoke(() -> searchOpsRpcService.reindex());
    }

    @GetMapping("/content/outbox/health")
    public ResponseEntity<Result<OutboxHealthResponse>> contentOutboxHealth() {
        return invoke(() -> contentOutboxRpcService.health());
    }

    @PostMapping("/content/outbox/replay")
    public ResponseEntity<Result<Integer>> contentOutboxReplay(@RequestParam(required = false) Integer limit) {
        return invoke(() -> contentOutboxRpcService.replayFailed(limit));
    }

    @GetMapping("/social/outbox/health")
    public ResponseEntity<Result<OutboxHealthResponse>> socialOutboxHealth() {
        return invoke(() -> socialOutboxRpcService.health());
    }

    @PostMapping("/social/outbox/replay")
    public ResponseEntity<Result<Integer>> socialOutboxReplay(@RequestParam(required = false) Integer limit) {
        return invoke(() -> socialOutboxRpcService.replayFailed(limit));
    }

    @GetMapping("/user/outbox/health")
    public ResponseEntity<Result<OutboxHealthResponse>> userOutboxHealth() {
        return invoke(() -> userOutboxRpcService.health());
    }

    @PostMapping("/user/outbox/replay")
    public ResponseEntity<Result<Integer>> userOutboxReplay(@RequestParam(required = false) Integer limit) {
        return invoke(() -> userOutboxRpcService.replayFailed(limit));
    }

    @PostMapping("/content/likes/backfill")
    public ResponseEntity<Result<ContentLikeBackfillResponse>> contentLikesBackfill(
            @RequestParam int entityType,
            @RequestParam(required = false) Long maxItems,
            @RequestParam(required = false) Integer batchSize
    ) {
        return invoke(() -> contentLikeOpsRpcService.backfill(entityType, maxItems, batchSize));
    }

    private <T> ResponseEntity<Result<T>> invoke(java.util.function.Supplier<Result<T>> call) {
        try {
            Result<T> result = call == null ? null : call.get();
            if (result == null) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Result.error(CommonErrorCode.SERVICE_UNAVAILABLE));
            }
            HttpStatus status = httpStatusOf(result);
            return ResponseEntity.status(status).body(result);
        } catch (RpcException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Result.error(CommonErrorCode.SERVICE_UNAVAILABLE));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.error(CommonErrorCode.INTERNAL_ERROR));
        }
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
