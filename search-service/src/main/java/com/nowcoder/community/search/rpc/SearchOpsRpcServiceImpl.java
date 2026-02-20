package com.nowcoder.community.search.rpc;

import com.nowcoder.community.common.api.CommonErrorCode;
import com.nowcoder.community.common.api.ErrorCode;
import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.search.api.SearchErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.search.api.rpc.SearchOpsRpcService;
import com.nowcoder.community.search.api.rpc.dto.SearchReindexResponse;
import com.nowcoder.community.search.service.PostSearchService;
import com.nowcoder.community.search.service.ReindexJobService;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.util.StringUtils;

@DubboService
public class SearchOpsRpcServiceImpl implements SearchOpsRpcService {

    private final PostSearchService postSearchService;
    private final ReindexJobService reindexJobService;

    public SearchOpsRpcServiceImpl(PostSearchService postSearchService, ReindexJobService reindexJobService) {
        this.postSearchService = postSearchService;
        this.reindexJobService = reindexJobService;
    }

    @Override
    public Result<SearchReindexResponse> reindex() {
        try {
            ReindexJobService.ReindexJob job = reindexJobService.tryStart();
            if (job == null || !job.acquired()) {
                reindexJobService.conflict(job == null ? null : job.jobId());
            }

            ReindexJobService.RenewalHandle renewal = reindexJobService.startRenewal(job.jobId());
            try {
                int count = postSearchService.clearAndReindexFromContentService();
                SearchReindexResponse resp = new SearchReindexResponse();
                resp.setJobId(job.jobId());
                resp.setIndexedCount(count);
                return Result.ok(resp);
            } finally {
                if (renewal != null) {
                    renewal.stop();
                }
                reindexJobService.finish(job.jobId());
            }
        } catch (BusinessException e) {
            return error(e);
        } catch (RuntimeException e) {
            return Result.error(SearchErrorCode.INTERNAL_ERROR);
        }
    }

    private <T> Result<T> error(BusinessException e) {
        if (e == null) {
            return Result.error(SearchErrorCode.INTERNAL_ERROR);
        }
        ErrorCode ec = e.getErrorCode() == null ? SearchErrorCode.INTERNAL_ERROR : e.getErrorCode();
        String msg = StringUtils.hasText(e.getMessage()) ? e.getMessage() : ec.getMessage();

        // 对于非 search 域错误码（例如 INVALID_ARGUMENT），保持语义保真
        if (ec == CommonErrorCode.INTERNAL_ERROR) {
            return Result.error(SearchErrorCode.INTERNAL_ERROR);
        }
        return Result.error(ec.getCode(), msg);
    }
}
