package com.nowcoder.community.user.rpc;

import com.nowcoder.community.contracts.api.CommonErrorCode;
import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.contracts.internal.dto.OutboxHealthResponse;
import com.nowcoder.community.infra.outbox.OutboxEventService;
import com.nowcoder.community.user.api.rpc.UserOutboxRpcService;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataAccessException;

@Service
public class UserOutboxRpcServiceImpl implements UserOutboxRpcService {

    private final OutboxEventService outboxEventService;

    public UserOutboxRpcServiceImpl(OutboxEventService outboxEventService) {
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
