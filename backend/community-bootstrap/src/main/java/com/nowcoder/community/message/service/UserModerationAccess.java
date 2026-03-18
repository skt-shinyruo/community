package com.nowcoder.community.message.service;

import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.contracts.internal.api.UserModerationApi;
import com.nowcoder.community.contracts.internal.dto.UserModerationStatus;
import com.nowcoder.community.infra.modulecall.ModuleCallOptions;
import com.nowcoder.community.infra.modulecall.ModuleCallSupport;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.nowcoder.community.contracts.api.CommonErrorCode.INVALID_ARGUMENT;

/**
 * user 模块治理接口访问：用于处罚状态查询与批量扫描（投影回填/纠偏）。
 */
@Service
public class UserModerationAccess {

    private static final Logger log = LoggerFactory.getLogger(UserModerationAccess.class);
    private static final String TARGET_MODULE = "user";

    private final MeterRegistry meterRegistry;
    private final UserModerationApi userModerationApi;

    public UserModerationAccess(MeterRegistry meterRegistry, UserModerationApi userModerationApi) {
        this.meterRegistry = meterRegistry;
        this.userModerationApi = userModerationApi;
    }

    public UserModerationStatus getStatus(int userId) {
        if (userId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        return ModuleCallSupport.callResult(
                meterRegistry,
                TARGET_MODULE,
                "getStatus",
                () -> userModerationApi.getStatus(userId),
                ModuleCallOptions.<UserModerationStatus>failClosed().withWarnLogger((m, e) -> log.warn(m, e))
        );
    }

    public List<UserModerationStatus> scanStatuses(int afterId, int limit) {
        int a = Math.max(0, afterId);
        int l = Math.min(500, Math.max(1, limit));
        List<UserModerationStatus> data = ModuleCallSupport.callResult(
                meterRegistry,
                TARGET_MODULE,
                "scanStatuses",
                () -> userModerationApi.scanStatuses(a, l),
                ModuleCallOptions.<List<UserModerationStatus>>failClosed().withWarnLogger((m, e) -> log.warn(m, e))
        );
        return data == null ? List.of() : data;
    }
}
