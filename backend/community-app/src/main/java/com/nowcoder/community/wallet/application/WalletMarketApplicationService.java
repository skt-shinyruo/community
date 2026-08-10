package com.nowcoder.community.wallet.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.api.action.WalletMarketActionApi;
import com.nowcoder.community.wallet.api.model.WalletMarketTxnView;
import com.nowcoder.community.wallet.application.result.WalletTxnResult;
import com.nowcoder.community.wallet.domain.model.WalletLedgerCommand;
import com.nowcoder.community.wallet.domain.model.WalletPosting;
import com.nowcoder.community.wallet.domain.model.WalletTxnType;
import com.nowcoder.community.wallet.domain.service.WalletAmountPolicy;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class WalletMarketApplicationService implements WalletMarketActionApi {

    private static final String ESCROW_ACCOUNT_TYPE = "ORDER_ESCROW";

    private final WalletAccountApplicationService walletAccountService;
    private final WalletLedgerApplicationService walletLedgerService;

    public WalletMarketApplicationService(WalletAccountApplicationService walletAccountService,
                                          WalletLedgerApplicationService walletLedgerService) {
        this.walletAccountService = Objects.requireNonNull(walletAccountService, "walletAccountService must not be null");
        this.walletLedgerService = Objects.requireNonNull(walletLedgerService, "walletLedgerService must not be null");
    }

    @Override
    @Transactional
    public WalletMarketTxnView escrowOrder(String requestId, UUID buyerUserId, long amount, String bizId) {
        validateRequest(requestId, buyerUserId, amount, bizId);
        walletAccountService.requireUserWalletActive(buyerUserId);
        WalletTxnResult result = walletLedgerService.post(new WalletLedgerCommand(
                requestId,
                WalletTxnType.ORDER_ESCROW,
                WalletTxnType.ORDER_ESCROW.name(),
                bizId,
                List.of(
                        WalletPosting.debit(walletAccountService.ensureUserWallet(buyerUserId), amount),
                        WalletPosting.credit(walletAccountService.ensureSystemAccount(ESCROW_ACCOUNT_TYPE), amount)
                )
        ));
        return new WalletMarketTxnView(result.txnId(), WalletTxnType.ORDER_ESCROW.name(), result.status(), amount, bizId);
    }

    @Override
    @Transactional
    public WalletMarketTxnView releaseOrder(String requestId, UUID sellerUserId, long amount, String bizId) {
        validateRequest(requestId, sellerUserId, amount, bizId);
        WalletTxnResult result = walletLedgerService.post(new WalletLedgerCommand(
                requestId,
                WalletTxnType.ORDER_RELEASE,
                WalletTxnType.ORDER_RELEASE.name(),
                bizId,
                List.of(
                        WalletPosting.debit(walletAccountService.ensureSystemAccount(ESCROW_ACCOUNT_TYPE), amount),
                        WalletPosting.credit(walletAccountService.ensureUserWallet(sellerUserId), amount)
                )
        ));
        return new WalletMarketTxnView(result.txnId(), WalletTxnType.ORDER_RELEASE.name(), result.status(), amount, bizId);
    }

    @Override
    @Transactional
    public WalletMarketTxnView refundOrder(String requestId, UUID buyerUserId, long amount, String bizId) {
        validateRequest(requestId, buyerUserId, amount, bizId);
        WalletTxnResult result = walletLedgerService.post(new WalletLedgerCommand(
                requestId,
                WalletTxnType.ORDER_REFUND,
                WalletTxnType.ORDER_REFUND.name(),
                bizId,
                List.of(
                        WalletPosting.debit(walletAccountService.ensureSystemAccount(ESCROW_ACCOUNT_TYPE), amount),
                        WalletPosting.credit(walletAccountService.ensureUserWallet(buyerUserId), amount)
                )
        ));
        return new WalletMarketTxnView(result.txnId(), WalletTxnType.ORDER_REFUND.name(), result.status(), amount, bizId);
    }

    private void validateRequest(String requestId, UUID userId, long amount, String bizId) {
        if (requestId == null || requestId.isBlank()) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "requestId must not be blank");
        }
        if (userId == null) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "userId must not be null");
        }
        if (amount <= 0) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "amount must be positive");
        }
        WalletAmountPolicy.validateAmount(amount);
        if (bizId == null || bizId.isBlank()) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "bizId must not be blank");
        }
    }
}
