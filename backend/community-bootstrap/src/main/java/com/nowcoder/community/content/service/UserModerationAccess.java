package com.nowcoder.community.content.service;

import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.contracts.internal.api.UserModerationApi;
import com.nowcoder.community.contracts.internal.dto.UserModerationStatus;
import com.nowcoder.community.infra.modulecall.ModuleCallOptions;
import com.nowcoder.community.infra.modulecall.ModuleCallSupport;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.nowcoder.community.contracts.api.CommonErrorCode.INVALID_ARGUMENT;

// user 模块治理接口访问：用于禁言/封禁与状态查询（internal 接口仅供模块间调用）。

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

    /**
     * internal 投影回填/纠偏：按主键游标批量扫描用户处罚状态。
     *
     * <p>用途：content 模块本地投影冷启动基线构建，避免投影缺失导致写路径不可用。</p>
     */
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
        ModuleCallSupport.callResult(
                meterRegistry,
                TARGET_MODULE,
                "apply:" + action,
                () -> userModerationApi.applyModeration(userId, action, seconds),
                ModuleCallOptions.<UserModerationStatus>failClosed().withWarnLogger((m, e) -> log.warn(m, e))
        );
    }
}
