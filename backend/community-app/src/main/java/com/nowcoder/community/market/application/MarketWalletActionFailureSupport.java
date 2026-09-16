package com.nowcoder.community.market.application;

import com.nowcoder.community.wallet.api.model.WalletErrorCodes;

import java.util.Set;

/**
 * Shared failure classification for market wallet actions: which wallet error codes
 * make a failed release/refund action safe to re-drive, and the last_error column bound.
 */
final class MarketWalletActionFailureSupport {

    static final int MAX_LAST_ERROR_LENGTH = 255;

    static final Set<String> RECOVERABLE_RELEASE_REFUND_FAILURE_CODES = Set.of(
            String.valueOf(WalletErrorCodes.ACCOUNT_UPDATE_CONFLICT),
            String.valueOf(WalletErrorCodes.ACCOUNT_BALANCE_INSUFFICIENT)
    );

    private MarketWalletActionFailureSupport() {
    }

    static String truncateLastError(String value) {
        return value.length() <= MAX_LAST_ERROR_LENGTH ? value : value.substring(0, MAX_LAST_ERROR_LENGTH);
    }
}
