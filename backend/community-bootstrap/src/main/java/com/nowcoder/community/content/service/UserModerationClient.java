package com.nowcoder.community.content.service;

import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.contracts.internal.dto.UserModerationStatus;
import com.nowcoder.community.contracts.internal.rpc.UserModerationRpcService;
import com.nowcoder.community.infra.internalclient.InternalCallOptions;
import com.nowcoder.community.infra.internalclient.InternalClientSupport;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.nowcoder.community.contracts.api.CommonErrorCode.INVALID_ARGUMENT;

// user-service 内部治理接口客户端：用于禁言/封禁与状态查询（开发阶段默认放行；生产建议通过网络隔离/网关策略收敛暴露面）。

@Service
public class UserModerationClient {

    private static final Logger log = LoggerFactory.getLogger(UserModerationClient.class);
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
        return InternalClientSupport.callResult(
                meterRegistry,
                SERVICE_NAME,
                "getStatus",
                () -> userModerationRpcService.getStatus(userId),
                InternalCallOptions.<UserModerationStatus>failClosed().withWarnLogger((m, e) -> log.warn(m, e))
        );
    }

    /**
     * internal 投影回填/纠偏：按主键游标批量扫描用户处罚状态。
     *
     * <p>用途：content-service 本地投影冷启动基线构建，避免投影缺失导致写路径不可用。</p>
     */
    public List<UserModerationStatus> scanStatuses(int afterId, int limit) {
        int a = Math.max(0, afterId);
        int l = Math.min(500, Math.max(1, limit));
        List<UserModerationStatus> data = InternalClientSupport.callResult(
                meterRegistry,
                SERVICE_NAME,
                "scanStatuses",
                () -> userModerationRpcService.scanStatuses(a, l),
                InternalCallOptions.<List<UserModerationStatus>>failClosed().withWarnLogger((m, e) -> log.warn(m, e))
        );
        return data == null ? List.of() : data;
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
        InternalClientSupport.callResult(
                meterRegistry,
                SERVICE_NAME,
                "apply:" + action,
                () -> userModerationRpcService.applyModeration(userId, action, seconds),
                InternalCallOptions.<UserModerationStatus>failClosed().withWarnLogger((m, e) -> log.warn(m, e))
        );
    }
}
