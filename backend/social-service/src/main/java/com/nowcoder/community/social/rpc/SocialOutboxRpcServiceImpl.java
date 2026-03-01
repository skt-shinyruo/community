package com.nowcoder.community.social.rpc;

import com.nowcoder.community.contracts.api.CommonErrorCode;
import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.contracts.internal.dto.OutboxHealthResponse;
import com.nowcoder.community.infra.outbox.OutboxEventService;
import com.nowcoder.community.social.api.rpc.SocialOutboxRpcService;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataAccessException;

@Service
public class SocialOutboxRpcServiceImpl implements SocialOutboxRpcService {

    private final OutboxEventService outboxEventService;

    public SocialOutboxRpcServiceImpl(OutboxEventService outboxEventService) {
        this.outboxEventService = outboxEventService;
    }

    @Override
    public Result<OutboxHealthResponse> health() {
        try {
            OutboxHealthResponse resp = new OutboxHealthResponse();
            resp.setNewCount(outboxEventService.countByStatus("NEW"));
            resp.setRetryCount(outboxEventService.countByStatus("RETRY"));
            resp.setSendingCount(outboxEventService.countByStatus("SENDING"));
            resp.setFailedCount(outboxEventService.countByStatus("FAILED"));
            return Result.ok(resp);
        } catch (DataAccessException e) {
            return Result.error(CommonErrorCode.SERVICE_UNAVAILABLE);
        } catch (RuntimeException e) {
            return Result.error(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public Result<Integer> replayFailed(Integer limit) {
        try {
            int l = limit == null ? 200 : limit;
            int n = outboxEventService.replayFailed(l);
            return Result.ok(n);
        } catch (IllegalArgumentException e) {
            return Result.error(CommonErrorCode.INVALID_ARGUMENT);
        } catch (DataAccessException e) {
            return Result.error(CommonErrorCode.SERVICE_UNAVAILABLE);
        } catch (RuntimeException e) {
            return Result.error(CommonErrorCode.INTERNAL_ERROR);
        }
    }
}
