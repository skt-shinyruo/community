package com.nowcoder.community.message.service;

import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.web.internalclient.InternalClientSupport;
import com.nowcoder.community.user.api.rpc.UserModerationRpcService;
import com.nowcoder.community.user.api.rpc.dto.UserModerationStatus;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.rpc.RpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.api.CommonErrorCode.SERVICE_UNAVAILABLE;

/**
 * user-service 内部治理接口客户端：用于处罚状态查询与批量扫描（投影回填/纠偏）。
 */
@Service
public class UserModerationClient {

    private static final Logger log = LoggerFactory.getLogger(UserModerationClient.class);
    private static final String SERVICE_NAME = "user-service";

    private final MeterRegistry meterRegistry;
    private final boolean failOpen;

    public UserModerationClient(
            MeterRegistry meterRegistry,
            @Value("${clients.user.fail-open:false}") boolean failOpen
    ) {
        this.meterRegistry = meterRegistry;
        this.failOpen = failOpen;
    }

    @DubboReference(check = false, retries = 0, timeout = 800)
    private UserModerationRpcService userModerationRpcService;

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
            if (failOpen) {
                InternalClientSupport.record(meterRegistry, SERVICE_NAME, "getStatus", InternalClientSupport.OUTCOME_DEGRADED, start);
                log.warn("[user-client] degraded (api=getStatus): {}", e.toString());
                UserModerationStatus status = new UserModerationStatus();
                status.setUserId(userId);
                return status;
            }
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
            if (failOpen) {
                InternalClientSupport.record(meterRegistry, SERVICE_NAME, "scanStatuses", InternalClientSupport.OUTCOME_DEGRADED, start);
                log.warn("[user-client] degraded (api=scanStatuses): {}", e.toString());
                return List.of();
            }
            String outcome = isTimeout(e) ? InternalClientSupport.OUTCOME_TIMEOUT : InternalClientSupport.OUTCOME_ERROR;
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "scanStatuses", outcome, start);
            if (e instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException(SERVICE_UNAVAILABLE, "user-service 不可用");
        }
    }

    private boolean isTimeout(Throwable t) {
        if (t instanceof RpcException re) {
            return re.isTimeout();
        }
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof RpcException re) {
                return re.isTimeout();
            }
            cur = cur.getCause();
        }
        return false;
    }
}
