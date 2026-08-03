package com.nowcoder.community.wallet.application;

import java.util.UUID;

public interface WalletTestCreditQuotaPort {

    Usage usage(UUID userId);

    boolean tryReserveGrant(UUID userId, long amount, long quota);

    boolean tryReserveDiscard(UUID userId, long amount, long quota);

    record Usage(long grantedAmount, long discardedAmount) {

        public Usage {
            if (grantedAmount < 0 || discardedAmount < 0) {
                throw new IllegalArgumentException("test credit usage must not be negative");
            }
        }

        public static Usage empty() {
            return new Usage(0L, 0L);
        }
    }
}
