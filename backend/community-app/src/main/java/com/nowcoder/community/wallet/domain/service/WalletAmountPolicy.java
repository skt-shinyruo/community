package com.nowcoder.community.wallet.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.exception.WalletErrorCode;

public final class WalletAmountPolicy {

    public static final long MAX_AMOUNT = 100_000_000L;

    private WalletAmountPolicy() {
    }

    public static void validateAmount(long amount) {
        if (amount <= 0) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "amount must be positive");
        }
        if (amount > MAX_AMOUNT) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "amount exceeds wallet maximum");
        }
    }

    public static long checkedAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ex) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "wallet amount overflow");
        }
    }
}
