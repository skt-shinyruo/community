package com.nowcoder.community.wallet.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.api.action.WalletRewardActionApi;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import com.nowcoder.community.wallet.model.WalletPosting;
import com.nowcoder.community.wallet.model.WalletTxnType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WalletRewardService implements WalletRewardActionApi {

    private final WalletAccountService walletAccountService;
    private final WalletLedgerService walletLedgerService;

    public WalletRewardService(WalletAccountService walletAccountService, WalletLedgerService walletLedgerService) {
        this.walletAccountService = walletAccountService;
        this.walletLedgerService = walletLedgerService;
    }

    @Transactional
    @Override
    public void issue(String requestId, int userId, long amount, String sourceType) {
        if (amount <= 0) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "reward amount must be positive");
        }

        walletLedgerService.post(
                requestId,
                WalletTxnType.REWARD_ISSUE,
                List.of(
                        WalletPosting.debit(walletAccountService.ensureSystemAccount("PLATFORM_REWARD_EXPENSE"), amount),
                        WalletPosting.credit(walletAccountService.ensureUserWallet(userId), amount)
                )
        );
    }

    @Transactional
    public void revoke(String requestId, int userId, long amount, String sourceType) {
        if (amount <= 0) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "reward revoke amount must be positive");
        }

        walletLedgerService.post(
                requestId,
                WalletTxnType.REWARD_ISSUE,
                List.of(
                        WalletPosting.debit(walletAccountService.ensureUserWallet(userId), amount),
                        WalletPosting.credit(walletAccountService.ensureSystemAccount("PLATFORM_REWARD_EXPENSE"), amount)
                )
        );
    }

    @Transactional
    @Override
    public void applyDelta(String requestId, int userId, long amount, String sourceType) {
        if (amount > 0) {
            issue(requestId, userId, amount, sourceType);
            return;
        }
        if (amount < 0) {
            revoke(requestId, userId, -amount, sourceType);
        }
    }
}
