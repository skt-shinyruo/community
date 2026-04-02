package com.nowcoder.community.wallet.model;

public record WalletPosting(long accountId, String direction, long amount) {

    public WalletPosting {
        if (accountId <= 0) {
            throw new IllegalArgumentException("accountId must be positive");
        }
        if (!"DEBIT".equals(direction) && !"CREDIT".equals(direction)) {
            throw new IllegalArgumentException("direction must be DEBIT or CREDIT");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }

    public static WalletPosting debit(long accountId, long amount) {
        return new WalletPosting(accountId, "DEBIT", amount);
    }

    public static WalletPosting credit(long accountId, long amount) {
        return new WalletPosting(accountId, "CREDIT", amount);
    }
}
