package com.nowcoder.community.content.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.service.UserModerationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

/**
 * 用户发言权限守卫（模块回源）：直接调用 user 模块的治理接口获取禁言/封禁状态。
 *
 * <p>说明：移除本地投影后，写路径会同步依赖 user 模块的实时可用性。</p>
 */
@Service("contentUserModerationGuard")
public class UserModerationGuard {

    private final UserModerationService userModerationService;

    @Autowired
    public UserModerationGuard(UserModerationService userModerationService) {
        this.userModerationService = userModerationService;
    }

    public void assertCanSpeak(int userId) {
        if (userId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }

        UserModerationService.ModerationStatus status = userModerationService.moderationStatus(userId);
        Instant now = Instant.now();

        if (status != null && status.getBanUntil() != null && status.getBanUntil().isAfter(now)) {
            throw new BusinessException(FORBIDDEN, "账号已被封禁，无法发言");
        }
        if (status != null && status.getMuteUntil() != null && status.getMuteUntil().isAfter(now)) {
            throw new BusinessException(FORBIDDEN, "你已被禁言，暂时无法发言");
        }
    }
}
