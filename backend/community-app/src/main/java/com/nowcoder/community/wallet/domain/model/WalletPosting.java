package com.nowcoder.community.wallet.domain.model;

import java.util.UUID;

public record WalletPosting(UUID accountId, String direction, long amount) {

    public WalletPosting {
        if (accountId == null) {
            throw new IllegalArgumentException("accountId must not be null");
        }
        if (!"DEBIT".equals(direction) && !"CREDIT".equals(direction)) {
            throw new IllegalArgumentException("direction must be DEBIT or CREDIT");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }

    public static WalletPosting debit(UUID accountId, long amount) {
        return new WalletPosting(accountId, "DEBIT", amount);
    }

    public static WalletPosting credit(UUID accountId, long amount) {
        return new WalletPosting(accountId, "CREDIT", amount);
    }
}
