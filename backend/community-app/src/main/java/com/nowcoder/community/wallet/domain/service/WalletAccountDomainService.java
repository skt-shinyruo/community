package com.nowcoder.community.wallet.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.domain.model.WalletPosting;
import com.nowcoder.community.wallet.exception.WalletErrorCode;

import java.util.Set;

public final class WalletAccountDomainService {

    public static final String OWNER_TYPE_USER = "USER";
    public static final String OWNER_TYPE_SYSTEM = "SYSTEM";
    public static final String ACCOUNT_TYPE_USER_WALLET = "USER_WALLET";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_FROZEN = "FROZEN";
    public static final String STATUS_UNKNOWN = "UNKNOWN";
    public static final String DIRECTION_DEBIT = "DEBIT";
    public static final String DIRECTION_CREDIT = "CREDIT";

    private static final Set<String> SYSTEM_ACCOUNT_TYPES = Set.of(
            "PLATFORM_CASH",
            "PLATFORM_REWARD_EXPENSE",
            "WITHDRAW_PENDING",
            "ORDER_ESCROW",
            "RISK_FROZEN",
            "MIGRATION_HOLD"
    );

    public void requireActive(String status) {
        if (!STATUS_ACTIVE.equals(status)) {
            throw new BusinessException(WalletErrorCode.ACCOUNT_FROZEN, "wallet account is frozen");
        }
    }

    public long deltaOf(String accountType, WalletPosting posting) {
        return deltaOf(normalDirectionOf(accountType), posting.direction(), posting.amount());
    }

    public long deltaOf(String normalDirection, String postingDirection, long amount) {
        return normalDirection.equals(postingDirection) ? amount : -amount;
    }

    public String normalDirectionOf(String accountType) {
        return switch (accountType) {
            case "PLATFORM_CASH", "PLATFORM_REWARD_EXPENSE", "MIGRATION_HOLD" -> DIRECTION_DEBIT;
            case ACCOUNT_TYPE_USER_WALLET, "WITHDRAW_PENDING", "ORDER_ESCROW", "RISK_FROZEN" -> DIRECTION_CREDIT;
            default -> throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "unsupported accountType=" + accountType);
        };
    }

    public void validateSystemAccountType(String accountType) {
        if (accountType == null || accountType.isBlank()) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "system accountType must not be blank");
        }
        if (!SYSTEM_ACCOUNT_TYPES.contains(accountType)) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "system accountType is not allowed: " + accountType);
        }
    }

    public void validateUserAccountStatus(String status) {
        if (!STATUS_ACTIVE.equals(status) && !STATUS_FROZEN.equals(status)) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "wallet account status is not allowed: " + status);
        }
    }
}
