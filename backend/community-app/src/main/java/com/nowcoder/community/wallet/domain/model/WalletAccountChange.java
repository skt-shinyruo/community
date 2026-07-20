package com.nowcoder.community.wallet.domain.model;

import java.util.Objects;
import java.util.UUID;

public record WalletAccountChange(
        UUID accountId,
        long expectedVersion,
        long delta,
        long nextBalance,
        String nextStatus,
        long nextVersion,
        WalletPostingPolicy policy
) {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_FROZEN = "FROZEN";

    public WalletAccountChange {
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(nextStatus, "nextStatus must not be null");
        Objects.requireNonNull(policy, "policy must not be null");
        if (expectedVersion < 0L) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
        if (!STATUS_ACTIVE.equals(nextStatus) && !STATUS_FROZEN.equals(nextStatus)) {
            throw new IllegalArgumentException("unsupported nextStatus: " + nextStatus);
        }
        long previousBalance;
        try {
            previousBalance = Math.subtractExact(nextBalance, delta);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("delta does not describe a valid balance change", exception);
        }
        if (policy == WalletPostingPolicy.NORMAL
                && delta < 0L
                && (previousBalance < 0L || nextBalance < 0L)) {
            throw new IllegalArgumentException("normal outgoing change must keep balances non-negative");
        }
        long requiredNextVersion;
        try {
            requiredNextVersion = Math.addExact(expectedVersion, 1L);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("expectedVersion cannot be advanced", exception);
        }
        if (nextVersion != requiredNextVersion) {
            throw new IllegalArgumentException("nextVersion must advance expectedVersion exactly once");
        }
    }
}
