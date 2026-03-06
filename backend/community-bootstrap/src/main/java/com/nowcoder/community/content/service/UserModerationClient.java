package com.nowcoder.community.content.service;

import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.contracts.internal.dto.UserModerationStatus;
import com.nowcoder.community.contracts.internal.rpc.UserModerationRpcService;
import com.nowcoder.community.infra.internalclient.InternalClientSupport;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.SocketTimeoutException;
import java.util.List;

import static com.nowcoder.community.contracts.api.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.contracts.api.CommonErrorCode.SERVICE_UNAVAILABLE;

// user-service 内部治理接口客户端：用于禁言/封禁与状态查询（开发阶段默认放行；生产建议通过网络隔离/网关策略收敛暴露面）。

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
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "getStatus", classifyOutcome(e), start);
            if (e instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException(SERVICE_UNAVAILABLE, "user-service 不可用");
        }
    }

    /**
     * internal 投影回填/纠偏：按主键游标批量扫描用户处罚状态。
     *
     * <p>用途：content-service 本地投影冷启动基线构建，避免投影缺失导致写路径不可用。</p>
     */
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
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "scanStatuses", classifyOutcome(e), start);
            if (e instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException(SERVICE_UNAVAILABLE, "user-service 不可用");
        }
    }

    public void mute(int userId, int durationSeconds) {
        apply(userId, "mute", durationSeconds);
    }

    public void ban(int userId, int durationSeconds) {
        apply(userId, "ban", durationSeconds);
    }

    private void apply(int userId, String action, int durationSeconds) {
        if (userId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        if (!StringUtils.hasText(action)) {
            throw new BusinessException(INVALID_ARGUMENT, "action 不能为空");
        }
        int seconds = Math.max(0, durationSeconds);
        long start = System.nanoTime();
        try {
            Result<UserModerationStatus> result = userModerationRpcService.applyModeration(userId, action, seconds);
            InternalClientSupport.unwrap(result, SERVICE_NAME);
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "apply:" + action, InternalClientSupport.OUTCOME_SUCCESS, start);
        } catch (RuntimeException e) {
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "apply:" + action, classifyOutcome(e), start);
            if (e instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException(SERVICE_UNAVAILABLE, "user-service 不可用");
        }
    }

    private String classifyOutcome(Throwable t) {
        if (isTimeout(t)) {
            return InternalClientSupport.OUTCOME_TIMEOUT;
        }
        return InternalClientSupport.OUTCOME_ERROR;
    }

    private boolean isTimeout(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof SocketTimeoutException) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }
}
