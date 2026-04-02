package com.nowcoder.community.wallet.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.entity.WalletAccount;
import com.nowcoder.community.wallet.entity.WalletEntry;
import com.nowcoder.community.wallet.entity.WalletTxn;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import com.nowcoder.community.wallet.mapper.WalletAccountMapper;
import com.nowcoder.community.wallet.mapper.WalletEntryMapper;
import com.nowcoder.community.wallet.mapper.WalletTxnMapper;
import com.nowcoder.community.wallet.model.WalletPosting;
import com.nowcoder.community.wallet.model.WalletTxnResult;
import com.nowcoder.community.wallet.model.WalletTxnType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class WalletLedgerService {

    private static final String OWNER_TYPE_USER = "USER";
    private static final String OWNER_TYPE_SYSTEM = "SYSTEM";
    private static final String ACCOUNT_TYPE_USER_WALLET = "USER_WALLET";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String TXN_STATUS_SUCCEEDED = "SUCCEEDED";
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
    private final WalletTxnMapper walletTxnMapper;
    private final WalletEntryMapper walletEntryMapper;

    public WalletLedgerService(WalletAccountMapper walletAccountMapper,
                               WalletTxnMapper walletTxnMapper,
                               WalletEntryMapper walletEntryMapper) {
        this.walletAccountMapper = walletAccountMapper;
        this.walletTxnMapper = walletTxnMapper;
        this.walletEntryMapper = walletEntryMapper;
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

    public List<WalletEntry> entriesOfTxn(long txnId) {
        return walletEntryMapper.selectByTxnId(txnId);
    }

    @Transactional
    public WalletTxnResult post(String requestId, WalletTxnType txnType, List<WalletPosting> postings) {
        if (requestId == null || requestId.isBlank()) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "requestId must not be blank");
        }
        if (txnType == null) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "txnType must not be null");
        }
        if (postings == null || postings.size() < 2) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "postings must contain at least two entries");
        }

        long debitTotal = postings.stream()
                .filter(posting -> DIRECTION_DEBIT.equals(posting.direction()))
                .mapToLong(WalletPosting::amount)
                .sum();
        long creditTotal = postings.stream()
                .filter(posting -> DIRECTION_CREDIT.equals(posting.direction()))
                .mapToLong(WalletPosting::amount)
                .sum();
        if (debitTotal <= 0 || debitTotal != creditTotal) {
            throw new BusinessException(WalletErrorCode.TXN_NOT_BALANCED, "wallet txn is not balanced: requestId=" + requestId);
        }

        WalletTxn txn = new WalletTxn();
        txn.setRequestId(requestId);
        txn.setTxnType(txnType.name());
        txn.setBizType(txnType.name());
        txn.setBizId(requestId);
        txn.setStatus(TXN_STATUS_SUCCEEDED);
        txn.setAmount(creditTotal);
        walletTxnMapper.insert(txn);

        for (WalletPosting posting : postings) {
            WalletAccount currentAccount = walletAccountMapper.selectByAccountId(posting.accountId());
            if (currentAccount == null) {
                throw new BusinessException(WalletErrorCode.ACCOUNT_NOT_FOUND, "wallet account not found: accountId=" + posting.accountId());
            }
            long delta = balanceDeltaOf(currentAccount.getAccountType(), posting.direction(), posting.amount());
            int updated = walletAccountMapper.updateBalanceWithVersion(
                    currentAccount.getAccountId(),
                    currentAccount.getVersion(),
                    delta,
                    currentAccount.getStatus()
            );
            if (updated != 1) {
                throw new BusinessException(
                        classifyUpdateFailure(currentAccount, delta),
                        "wallet account update failed: accountId=" + currentAccount.getAccountId()
                );
            }
            WalletAccount accountAfter = walletAccountMapper.selectByAccountId(currentAccount.getAccountId());

            WalletEntry entry = new WalletEntry();
            entry.setTxnId(txn.getTxnId());
            entry.setAccountId(currentAccount.getAccountId());
            entry.setDirection(posting.direction());
            entry.setAmount(posting.amount());
            entry.setBalanceAfter(accountAfter.getBalance());
            walletEntryMapper.insert(entry);
        }

        return new WalletTxnResult(txn.getTxnId());
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

    private long balanceDeltaOf(String accountType, String direction, long amount) {
        String normalDirection = normalDirectionOf(accountType);
        return normalDirection.equals(direction) ? amount : -amount;
    }

    private String normalDirectionOf(String accountType) {
        return switch (accountType) {
            case "PLATFORM_CASH", "PLATFORM_REWARD_EXPENSE" -> DIRECTION_DEBIT;
            case ACCOUNT_TYPE_USER_WALLET, "WITHDRAW_PENDING", "ORDER_ESCROW", "RISK_FROZEN", "MIGRATION_HOLD" -> DIRECTION_CREDIT;
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
}
