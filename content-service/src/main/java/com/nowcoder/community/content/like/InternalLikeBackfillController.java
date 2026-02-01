package com.nowcoder.community.content.like;

import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.domain.EntityTypes;
import com.nowcoder.community.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.nowcoder.community.common.api.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT;

/**
 * content-service internal 运维入口：回填 Redis 点赞投影（减少冷启动窗口）。
 *
 * <p>默认关闭：需要显式打开开关后才允许执行，避免误触造成压力。</p>
 */
@RestController
@RequestMapping("/internal/content/likes")
@ConditionalOnProperty(name = "content.storage", havingValue = "redis", matchIfMissing = true)
public class InternalLikeBackfillController {

    private final LikeProjectionBackfillJob backfillJob;
    private final boolean endpointEnabled;

    public InternalLikeBackfillController(
            LikeProjectionBackfillJob backfillJob,
            @Value("${content.like.backfill.endpoint-enabled:false}") boolean endpointEnabled
    ) {
        this.backfillJob = backfillJob;
        this.endpointEnabled = endpointEnabled;
    }

    @PostMapping("/backfill")
    public Result<LikeProjectionBackfillJob.BackfillResult> backfill(
            @RequestParam int entityType,
            @RequestParam(required = false) Long maxItems,
            @RequestParam(required = false) Integer batchSize
    ) {
        if (!endpointEnabled) {
            throw new BusinessException(FORBIDDEN, "like backfill endpoint disabled");
        }
        if (!EntityTypes.isValid(entityType)) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType 非法");
        }
        long max = maxItems == null ? 100_000L : Math.max(1L, maxItems);
        int bs = batchSize == null ? 1000 : Math.min(2000, Math.max(1, batchSize));
        return Result.ok(backfillJob.backfill(entityType, max, bs));
    }
}

