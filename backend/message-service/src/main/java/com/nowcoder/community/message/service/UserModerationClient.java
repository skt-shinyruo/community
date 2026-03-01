package com.nowcoder.community.message.service;

import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.contracts.internal.dto.UserModerationStatus;
import com.nowcoder.community.contracts.internal.rpc.UserModerationRpcService;
import com.nowcoder.community.platform.web.internalclient.InternalClientSupport;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.nowcoder.community.contracts.api.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.contracts.api.CommonErrorCode.SERVICE_UNAVAILABLE;

/**
 * user-service 内部治理接口客户端：用于处罚状态查询与批量扫描（投影回填/纠偏）。
 */
@Service
public class UserModerationClient {

    private static final String SERVICE_NAME = "user-service";

    private final MeterRegistry meterRegistry;
    private final UserModerationRpcService userModerationRpcService;

    public UserModerationClient(MeterRegistry meterRegistry, UserModerationRpcService userModerationRpcService) {
        this.meterRegistry = meterRegistry;
        this.userModerationRpcService = userModerationRpcService;
    }

    public UserModerationStatus getStatus(int userId) {
        if (userId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        long start = System.nanoTime();
        try {
            Result<UserModerationStatus> result = userModerationRpcService.getStatus(userId);
            UserModerationStatus data = InternalClientSupport.unwrap(result, SERVICE_NAME);
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "getStatus", InternalClientSupport.OUTCOME_SUCCESS, start);
            return data;
        } catch (RuntimeException e) {
            String outcome = isTimeout(e) ? InternalClientSupport.OUTCOME_TIMEOUT : InternalClientSupport.OUTCOME_ERROR;
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "getStatus", outcome, start);
            if (e instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException(SERVICE_UNAVAILABLE, "user-service 不可用");
        }
    }

    public List<UserModerationStatus> scanStatuses(int afterId, int limit) {
        int a = Math.max(0, afterId);
        int l = Math.min(500, Math.max(1, limit));
        long start = System.nanoTime();
        try {
            Result<List<UserModerationStatus>> result = userModerationRpcService.scanStatuses(a, l);
            List<UserModerationStatus> data = InternalClientSupport.unwrap(result, SERVICE_NAME);
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "scanStatuses", InternalClientSupport.OUTCOME_SUCCESS, start);
            return data == null ? List.of() : data;
        } catch (RuntimeException e) {
            String outcome = isTimeout(e) ? InternalClientSupport.OUTCOME_TIMEOUT : InternalClientSupport.OUTCOME_ERROR;
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "scanStatuses", outcome, start);
            if (e instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException(SERVICE_UNAVAILABLE, "user-service 不可用");
        }
    }

    private boolean isTimeout(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            cur = cur.getCause();
        }
        return false;
    }
}
