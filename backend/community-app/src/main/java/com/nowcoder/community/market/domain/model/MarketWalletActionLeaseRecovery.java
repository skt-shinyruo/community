package com.nowcoder.community.market.domain.model;

import java.util.Date;
import java.util.Objects;
import java.util.UUID;

public record MarketWalletActionLeaseRecovery(
        UUID actionId,
        UUID expectedLeaseToken,
        Date expectedLeaseUntil,
        int expectedRetryCount,
        Date asOf,
        Date nextRetryAt,
        int maxRetryAttempts,
        String lastError
) {

    public MarketWalletActionLeaseRecovery {
        Objects.requireNonNull(actionId, "actionId must not be null");
        Objects.requireNonNull(expectedLeaseUntil, "expectedLeaseUntil must not be null");
        Objects.requireNonNull(asOf, "asOf must not be null");
        Objects.requireNonNull(nextRetryAt, "nextRetryAt must not be null");
        if (expectedRetryCount < 0) {
            throw new IllegalArgumentException("expectedRetryCount must not be negative");
        }
        if (maxRetryAttempts <= 0) {
            throw new IllegalArgumentException("maxRetryAttempts must be positive");
        }
    }
}
