package com.nowcoder.community.wallet.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.application.command.WalletRewardCommand;
import com.nowcoder.community.wallet.domain.model.WalletPosting;
import com.nowcoder.community.wallet.domain.model.WalletTxnType;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class WalletRewardApplicationService {

    private static final String PLATFORM_REWARD_EXPENSE = "PLATFORM_REWARD_EXPENSE";
    private final WalletAccountApplicationService walletAccountService;
    private final WalletLedgerApplicationService walletLedgerService;

    public WalletRewardApplicationService(WalletAccountApplicationService walletAccountService, WalletLedgerApplicationService walletLedgerService) {
        this.walletAccountService = walletAccountService;
        this.walletLedgerService = walletLedgerService;
    }

    @Transactional
    public void issue(WalletRewardCommand command) {
        if (command.amount() <= 0) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "reward amount must be positive");
        }
        postRewardTxn(command.requestId(), command.userId(), command.amount(), command.sourceType(), WalletTxnType.REWARD_ISSUE);
    }

    @Transactional
    public void revoke(WalletRewardCommand command) {
        if (command.amount() <= 0) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "reward amount must be positive");
        }
        postRewardTxn(command.requestId(), command.userId(), -command.amount(), command.sourceType(), WalletTxnType.REWARD_ISSUE);
    }

    @Transactional
    public void applyDelta(WalletRewardCommand command) {
        if (command.amount() == 0) {
            return;
        }
        postRewardTxn(command.requestId(), command.userId(), command.amount(), command.sourceType(), WalletTxnType.REWARD_ISSUE);
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
        if (requestId == null || requestId.isBlank()) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "requestId must not be blank");
        }
        if (userId == null) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "userId must not be null");
        }
        if (sourceType == null || sourceType.isBlank()) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "sourceType must not be blank");
        }
    }
}
