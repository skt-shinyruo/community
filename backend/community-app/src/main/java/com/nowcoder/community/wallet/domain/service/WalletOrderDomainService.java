package com.nowcoder.community.wallet.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.exception.WalletErrorCode;

import java.util.UUID;

public final class WalletOrderDomainService {

    public void validatePositiveAmount(long amount) {
        if (amount <= 0) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "amount must be positive");
        }
    }

    public void validateTransfer(UUID fromUserId, UUID toUserId, long amount) {
        if (fromUserId == null || toUserId == null) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "userId must not be null");
        }
        validatePositiveAmount(amount);
        if (fromUserId.equals(toUserId)) {
            throw new BusinessException(WalletErrorCode.INVALID_TRANSFER, "cannot transfer to self");
        }
    }
}
