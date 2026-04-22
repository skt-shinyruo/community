package com.nowcoder.community.wallet.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.api.action.WalletRewardActionApi;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import com.nowcoder.community.wallet.model.WalletPosting;
import com.nowcoder.community.wallet.model.WalletTxnType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Service
public class WalletRewardService implements WalletRewardActionApi {

    private static final String PLATFORM_REWARD_EXPENSE = "PLATFORM_REWARD_EXPENSE";
    private final WalletAccountService walletAccountService;
    private final WalletLedgerService walletLedgerService;

    public WalletRewardService(WalletAccountService walletAccountService, WalletLedgerService walletLedgerService) {
        this.walletAccountService = walletAccountService;
        this.walletLedgerService = walletLedgerService;
    }

    @Transactional
    @Override
    public void issue(String requestId, UUID userId, long amount, String sourceType) {
        if (amount <= 0) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "reward amount must be positive");
        }
        postRewardTxn(requestId, userId, amount, sourceType, WalletTxnType.REWARD_ISSUE);
    }

    @Transactional
    public void revoke(String requestId, UUID userId, long amount, String sourceType) {
        if (amount <= 0) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "reward amount must be positive");
        }
        postRewardTxn(requestId, userId, -amount, sourceType, WalletTxnType.REWARD_ISSUE);
    }

    @Transactional
    @Override
    public void applyDelta(String requestId, UUID userId, long amount, String sourceType) {
        if (amount == 0) {
            return;
        }
        postRewardTxn(requestId, userId, amount, sourceType, WalletTxnType.REWARD_ISSUE);
    }

    private void postRewardTxn(String requestId, UUID userId, long amount, String sourceType, WalletTxnType txnType) {
        validateRequest(requestId, userId, sourceType);
        UUID userWalletId = walletAccountService.ensureUserWallet(userId);
        UUID systemAccountId = walletAccountService.ensureSystemAccount(PLATFORM_REWARD_EXPENSE);
        long absoluteAmount = Math.abs(amount);
        List<WalletPosting> postings = amount > 0
                ? List.of(
                WalletPosting.debit(systemAccountId, absoluteAmount),
                WalletPosting.credit(userWalletId, absoluteAmount)
        )
                : List.of(
                WalletPosting.debit(userWalletId, absoluteAmount),
                WalletPosting.credit(systemAccountId, absoluteAmount)
        );
        walletLedgerService.post(requestId, txnType, postings);
    }

    private void validateRequest(String requestId, UUID userId, String sourceType) {
        if (!StringUtils.hasText(requestId)) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "requestId must not be blank");
        }
        if (userId == null) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "userId must not be null");
        }
        if (!StringUtils.hasText(sourceType)) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "sourceType must not be blank");
        }
    }
}
