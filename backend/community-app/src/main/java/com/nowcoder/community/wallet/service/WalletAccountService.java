package com.nowcoder.community.wallet.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.entity.WalletAccount;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import com.nowcoder.community.wallet.mapper.WalletAccountMapper;
import com.nowcoder.community.wallet.model.WalletPosting;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class WalletAccountService {

    private static final String OWNER_TYPE_USER = "USER";
    private static final String OWNER_TYPE_SYSTEM = "SYSTEM";
    private static final String ACCOUNT_TYPE_USER_WALLET = "USER_WALLET";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_FROZEN = "FROZEN";
    private static final String DIRECTION_DEBIT = "DEBIT";
    private static final String DIRECTION_CREDIT = "CREDIT";
    private static final Set<String> SYSTEM_ACCOUNT_TYPES = Set.of(
            "PLATFORM_CASH",
            "PLATFORM_REWARD_EXPENSE",
            "WITHDRAW_PENDING",
            "ORDER_ESCROW",
            "RISK_FROZEN",
            "MIGRATION_HOLD"
    );

    private final WalletAccountMapper walletAccountMapper;

    public WalletAccountService(WalletAccountMapper walletAccountMapper) {
        this.walletAccountMapper = walletAccountMapper;
    }

    public long ensureUserWallet(long userId) {
        return ensureAccount(OWNER_TYPE_USER, userId, ACCOUNT_TYPE_USER_WALLET).getAccountId();
    }

    public long ensureSystemAccount(String accountType) {
        validateSystemAccountType(accountType);
        return ensureAccount(OWNER_TYPE_SYSTEM, 0L, accountType).getAccountId();
    }

    public long balanceOfUser(long userId) {
        WalletAccount account = walletAccountMapper.selectByOwner(OWNER_TYPE_USER, userId, ACCOUNT_TYPE_USER_WALLET);
        return account == null ? 0L : account.getBalance();
    }

    public long balanceOfSystem(String accountType) {
        validateSystemAccountType(accountType);
        WalletAccount account = walletAccountMapper.selectByOwner(OWNER_TYPE_SYSTEM, 0L, accountType);
        return account == null ? 0L : account.getBalance();
    }

    public WalletAccount loadUserWallet(long userId) {
        return ensureAccount(OWNER_TYPE_USER, userId, ACCOUNT_TYPE_USER_WALLET);
    }

    public void requireUserWalletActive(long userId) {
        WalletAccount account = loadUserWallet(userId);
        if (!STATUS_ACTIVE.equals(account.getStatus())) {
            throw new BusinessException(WalletErrorCode.ACCOUNT_FROZEN, "wallet account is frozen: userId=" + userId);
        }
    }

    public void setStatus(long accountId, String nextStatus) {
        validateUserAccountStatus(nextStatus);
        WalletAccount account = lock(accountId);
        account.setStatus(nextStatus);
        apply(account, 0L);
    }

    public WalletAccount lock(long accountId) {
        WalletAccount account = walletAccountMapper.selectByAccountId(accountId);
        if (account == null) {
            throw new BusinessException(WalletErrorCode.ACCOUNT_NOT_FOUND, "wallet account not found: accountId=" + accountId);
        }
        return account;
    }

    public long deltaOf(WalletAccount account, WalletPosting posting) {
        String normalDirection = normalDirectionOf(account.getAccountType());
        return normalDirection.equals(posting.direction()) ? posting.amount() : -posting.amount();
    }

    public long apply(WalletAccount account, long delta) {
        int updated = walletAccountMapper.updateBalanceWithVersion(
                account.getAccountId(),
                account.getVersion(),
                delta,
                account.getStatus()
        );
        if (updated != 1) {
            throw new BusinessException(
                    classifyUpdateFailure(account, delta),
                    "wallet account update failed: accountId=" + account.getAccountId()
            );
        }

        WalletAccount latest = walletAccountMapper.selectByAccountId(account.getAccountId());
        if (latest == null) {
            throw new BusinessException(WalletErrorCode.ACCOUNT_NOT_FOUND, "wallet account not found: accountId=" + account.getAccountId());
        }
        return latest.getBalance();
    }

    private WalletAccount ensureAccount(String ownerType, long ownerId, String accountType) {
        WalletAccount existing = walletAccountMapper.selectByOwner(ownerType, ownerId, accountType);
        if (existing != null) {
            return existing;
        }

        WalletAccount account = new WalletAccount();
        account.setOwnerType(ownerType);
        account.setOwnerId(ownerId);
        account.setAccountType(accountType);
        account.setBalance(0L);
        account.setStatus(STATUS_ACTIVE);
        account.setVersion(0L);
        try {
            walletAccountMapper.insert(account);
            return account;
        } catch (DataIntegrityViolationException ignored) {
            WalletAccount duplicated = walletAccountMapper.selectByOwner(ownerType, ownerId, accountType);
            if (duplicated != null) {
                return duplicated;
            }
            throw ignored;
        }
    }

    private WalletErrorCode classifyUpdateFailure(WalletAccount beforeAccount, long delta) {
        WalletAccount latestAccount = walletAccountMapper.selectByAccountId(beforeAccount.getAccountId());
        if (latestAccount == null) {
            return WalletErrorCode.ACCOUNT_NOT_FOUND;
        }
        if (latestAccount.getVersion() != beforeAccount.getVersion()) {
            return WalletErrorCode.ACCOUNT_UPDATE_CONFLICT;
        }
        if (delta < 0 && latestAccount.getBalance() + delta < 0) {
            return WalletErrorCode.ACCOUNT_BALANCE_INSUFFICIENT;
        }
        return WalletErrorCode.ACCOUNT_UPDATE_CONFLICT;
    }

    private String normalDirectionOf(String accountType) {
        return switch (accountType) {
            case "PLATFORM_CASH", "PLATFORM_REWARD_EXPENSE", "MIGRATION_HOLD" -> DIRECTION_DEBIT;
            case ACCOUNT_TYPE_USER_WALLET, "WITHDRAW_PENDING", "ORDER_ESCROW", "RISK_FROZEN" -> DIRECTION_CREDIT;
            default -> throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "unsupported accountType=" + accountType);
        };
    }

    private void validateSystemAccountType(String accountType) {
        if (accountType == null || accountType.isBlank()) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "system accountType must not be blank");
        }
        if (!SYSTEM_ACCOUNT_TYPES.contains(accountType)) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "system accountType is not allowed: " + accountType);
        }
    }

    private void validateUserAccountStatus(String status) {
        if (!STATUS_ACTIVE.equals(status) && !STATUS_FROZEN.equals(status)) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "wallet account status is not allowed: " + status);
        }
    }
}
