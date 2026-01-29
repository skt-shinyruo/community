package com.nowcoder.community.message.service;

import com.nowcoder.community.common.api.CommonErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.message.projection.UserModerationProjectionRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 私信权限守卫：优先读取本地投影（最终一致），投影缺失时 bootstrap 一次后再校验。
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

    public void assertCanSendMessage(int userId) {
        try {
            projectionRepository.assertCanSendMessage(userId);
            return;
        } catch (BusinessException e) {
            if (!isProjectionMissing(e)) {
                throw e;
            }
        }

        UserModerationClient.ModerationStatus status = userModerationClient.getStatus(userId);
        projectionRepository.upsertModerationStatus(
                userId,
                status == null ? null : status.getMuteUntil(),
                status == null ? null : status.getBanUntil(),
                Instant.now()
        );
        projectionRepository.assertCanSendMessage(userId);
    }

    private boolean isProjectionMissing(BusinessException e) {
        if (e == null || e.getErrorCode() == null) {
            return false;
        }
        return e.getErrorCode() == CommonErrorCode.SERVICE_UNAVAILABLE
                && String.valueOf(e.getMessage()).contains("投影缺失");
    }
}

