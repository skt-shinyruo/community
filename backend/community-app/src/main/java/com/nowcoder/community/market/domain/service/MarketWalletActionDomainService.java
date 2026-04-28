package com.nowcoder.community.market.domain.service;

import com.nowcoder.community.common.exception.BusinessException;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

public final class MarketWalletActionDomainService {

    private static final Set<String> TERMINAL_STATUSES = Set.of("SUCCEEDED", "CANCELLED", "FAILED", "DEAD");

    public String requestId(String actionType, UUID orderId) {
        if (orderId == null || actionType == null || actionType.isBlank()) {
            throw new BusinessException(INVALID_ARGUMENT, "market wallet action request fields must not be blank");
        }
        return "market-order:" + orderId + ":" + actionType.toLowerCase(Locale.ROOT);
    }

    public void validateTerminalTransition(String currentStatus, String nextStatus) {
        if (TERMINAL_STATUSES.contains(currentStatus) && !currentStatus.equals(nextStatus)) {
            throw new BusinessException(INVALID_ARGUMENT, "terminal market wallet action cannot transition");
        }
    }
}
