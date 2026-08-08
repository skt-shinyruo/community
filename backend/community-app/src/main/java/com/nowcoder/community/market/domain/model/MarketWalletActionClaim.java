package com.nowcoder.community.market.domain.model;

import java.util.Date;
import java.util.Objects;

public record MarketWalletActionClaim(
        MarketWalletActionLease lease,
        String expectedStatus,
        int expectedRetryCount,
        Date claimedAt,
        Date leaseUntil,
        int maxRetryAttempts
) {

    public MarketWalletActionClaim {
        Objects.requireNonNull(lease, "lease must not be null");
        Objects.requireNonNull(expectedStatus, "expectedStatus must not be null");
        Objects.requireNonNull(claimedAt, "claimedAt must not be null");
        Objects.requireNonNull(leaseUntil, "leaseUntil must not be null");
        if (expectedRetryCount < 0) {
            throw new IllegalArgumentException("expectedRetryCount must not be negative");
        }
        if (!MarketWalletActionStatus.PENDING.equals(expectedStatus)
                && !MarketWalletActionStatus.RETRYING.equals(expectedStatus)) {
            throw new IllegalArgumentException("expectedStatus must be PENDING or RETRYING");
        }
        if (maxRetryAttempts <= 0) {
            throw new IllegalArgumentException("maxRetryAttempts must be positive");
        }
        if (!leaseUntil.after(claimedAt)) {
            throw new IllegalArgumentException("leaseUntil must be after claimedAt");
        }
    }
}
