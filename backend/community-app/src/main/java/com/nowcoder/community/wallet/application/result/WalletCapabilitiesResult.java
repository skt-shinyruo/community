package com.nowcoder.community.wallet.application.result;

public record WalletCapabilitiesResult(
        String balanceUnit,
        boolean realPaymentsSupported,
        boolean realPayoutsSupported,
        TestCredits testCredits
) {

    public record TestCredits(boolean enabled, Action grant, Action discard) {
    }

    public record Action(
            boolean enabled,
            long maxAmountPerRequest,
            long totalQuota,
            long usedAmount,
            long remainingAmount
    ) {
    }
}
