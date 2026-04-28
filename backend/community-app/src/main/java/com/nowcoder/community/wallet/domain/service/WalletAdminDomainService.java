package com.nowcoder.community.wallet.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.exception.WalletErrorCode;

import java.util.UUID;

public final class WalletAdminDomainService {

    public String validateAdminAction(UUID actorUserId, String reason) {
        if (actorUserId == null) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "actorUserId must not be null");
        }
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.isBlank()) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "reason must not be blank");
        }
        return normalizedReason;
    }
}
