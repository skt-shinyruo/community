package com.nowcoder.community.content.service;

import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.content.api.ContentErrorCode;
import com.nowcoder.community.content.projection.UserModerationProjectionRepository;
import com.nowcoder.community.user.api.rpc.dto.UserModerationStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 用户发言权限守卫：优先读取本地投影（最终一致），投影缺失时进行一次性 bootstrap 回填后再校验。
 *
 * <p>说明：正常路径不依赖 user-service 的实时可用性；bootstrap 仅用于冷启动/缺失场景的纠偏。</p>
 */
@Service
public class UserModerationGuard {

    private final UserModerationProjectionRepository projectionRepository;
    private final UserModerationClient userModerationClient;

    public UserModerationGuard(
            UserModerationProjectionRepository projectionRepository,
            UserModerationClient userModerationClient
    ) {
        this.projectionRepository = projectionRepository;
        this.userModerationClient = userModerationClient;
    }

    public void assertCanSpeak(int userId) {
        try {
            projectionRepository.assertCanSpeak(userId);
            return;
        } catch (BusinessException e) {
            if (!isProjectionMissing(e)) {
                throw e;
            }
        }

        // 投影缺失：向 SSOT(user-service) 做一次性回填，再次校验（失败则按 fail-closed 返回 503）。
        UserModerationStatus status = userModerationClient.getStatus(userId);
        projectionRepository.upsertModerationStatus(
                userId,
                status == null ? null : status.getMuteUntil(),
                status == null ? null : status.getBanUntil(),
                Instant.now()
        );
        projectionRepository.assertCanSpeak(userId);
    }

    private boolean isProjectionMissing(BusinessException e) {
        if (e == null || e.getErrorCode() == null) {
            return false;
        }
        return e.getErrorCode() == ContentErrorCode.PROJECTION_MISSING;
    }
}
