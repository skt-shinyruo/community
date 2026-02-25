package com.nowcoder.community.content.rpc;

import com.nowcoder.community.contracts.api.CommonErrorCode;
import com.nowcoder.community.contracts.api.ErrorCode;
import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.contracts.domain.EntityTypes;
import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.content.api.rpc.ContentLikeOpsRpcService;
import com.nowcoder.community.content.api.rpc.dto.ContentLikeBackfillResponse;
import com.nowcoder.community.content.like.LikeProjectionBackfillJob;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.StringUtils;

import static com.nowcoder.community.contracts.api.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.contracts.api.CommonErrorCode.INVALID_ARGUMENT;

@DubboService
@ConditionalOnProperty(name = "content.storage", havingValue = "redis", matchIfMissing = true)
public class ContentLikeOpsRpcServiceImpl implements ContentLikeOpsRpcService {

    private final LikeProjectionBackfillJob backfillJob;
    private final boolean endpointEnabled;

    public ContentLikeOpsRpcServiceImpl(
            LikeProjectionBackfillJob backfillJob,
            @org.springframework.beans.factory.annotation.Value("${content.like.backfill.endpoint-enabled:false}") boolean endpointEnabled
    ) {
        this.backfillJob = backfillJob;
        this.endpointEnabled = endpointEnabled;
    }

    @Override
    public Result<ContentLikeBackfillResponse> backfill(int entityType, Long maxItems, Integer batchSize) {
        try {
            if (!endpointEnabled) {
                throw new BusinessException(FORBIDDEN, "like backfill endpoint disabled");
            }
            if (!EntityTypes.isValid(entityType)) {
                throw new BusinessException(INVALID_ARGUMENT, "entityType 非法");
            }
            long max = maxItems == null ? 100_000L : Math.max(1L, maxItems);
            int bs = batchSize == null ? 1000 : Math.min(2000, Math.max(1, batchSize));

            LikeProjectionBackfillJob.BackfillResult r = backfillJob.backfill(entityType, max, bs);
            if (r == null) {
                return Result.ok(new ContentLikeBackfillResponse());
            }
            ContentLikeBackfillResponse resp = new ContentLikeBackfillResponse();
            resp.setEntityType(r.getEntityType());
            resp.setScannedItems(r.getScannedItems());
            resp.setAddedMembers(r.getAddedMembers());
            resp.setPages(r.getPages());
            return Result.ok(resp);
        } catch (BusinessException e) {
            return error(e);
        } catch (RuntimeException e) {
            return Result.error(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    private <T> Result<T> error(BusinessException e) {
        if (e == null) {
            return Result.error(CommonErrorCode.INTERNAL_ERROR);
        }
        ErrorCode ec = e.getErrorCode() == null ? CommonErrorCode.INTERNAL_ERROR : e.getErrorCode();
        String msg = StringUtils.hasText(e.getMessage()) ? e.getMessage() : ec.getMessage();
        return Result.error(ec.getCode(), msg);
    }
}

