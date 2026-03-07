package com.nowcoder.community.message.service;

import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.contracts.internal.dto.UserModerationStatus;
import com.nowcoder.community.contracts.internal.rpc.UserModerationRpcService;
import com.nowcoder.community.infra.internalclient.InternalCallOptions;
import com.nowcoder.community.infra.internalclient.InternalClientSupport;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.nowcoder.community.contracts.api.CommonErrorCode.INVALID_ARGUMENT;

/**
 * user-service 内部治理接口客户端：用于处罚状态查询与批量扫描（投影回填/纠偏）。
 */
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
}
