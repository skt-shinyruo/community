package com.nowcoder.community.wallet.domain.model;

import java.util.Objects;
import java.util.UUID;

public record RechargeOrderTransition(
        UUID orderId,
        UUID userId,
        String requestId,
        RechargeOrderStatus fromStatus,
        RechargeOrderStatus toStatus
) {
    public RechargeOrderTransition {
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        Objects.requireNonNull(fromStatus, "fromStatus must not be null");
        Objects.requireNonNull(toStatus, "toStatus must not be null");
    }
}
