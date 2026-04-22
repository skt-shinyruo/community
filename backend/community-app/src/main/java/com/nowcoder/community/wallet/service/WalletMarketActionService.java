package com.nowcoder.community.wallet.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.api.action.WalletMarketActionApi;
import com.nowcoder.community.wallet.api.model.WalletMarketTxnView;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import com.nowcoder.community.wallet.model.WalletPosting;
import com.nowcoder.community.wallet.model.WalletTxnResult;
import com.nowcoder.community.wallet.model.WalletTxnType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Service
public class WalletMarketActionService implements WalletMarketActionApi {

    private static final String ESCROW_ACCOUNT_TYPE = "ORDER_ESCROW";

    private final WalletAccountService walletAccountService;
    private final WalletLedgerService walletLedgerService;

    public WalletMarketActionService(WalletAccountService walletAccountService,
                                     WalletLedgerService walletLedgerService) {
        this.walletAccountService = walletAccountService;
        this.walletLedgerService = walletLedgerService;
    }

    @Transactional
    @Override
    public WalletMarketTxnView escrowOrder(String requestId, UUID buyerUserId, long amount, String bizId) {
        validateRequest(requestId, buyerUserId, amount, bizId);
        walletAccountService.requireUserWalletActive(buyerUserId);
        WalletTxnResult result = walletLedgerService.post(
                requestId,
                WalletTxnType.ORDER_ESCROW,
                List.of(
                        WalletPosting.debit(walletAccountService.ensureUserWallet(buyerUserId), amount),
                        WalletPosting.credit(walletAccountService.ensureSystemAccount(ESCROW_ACCOUNT_TYPE), amount)
                )
        );
        return new WalletMarketTxnView(result.txnId(), WalletTxnType.ORDER_ESCROW.name(), result.status(), amount, bizId);
    }

    @Transactional
    @Override
    public WalletMarketTxnView releaseOrder(String requestId, UUID sellerUserId, long amount, String bizId) {
        validateRequest(requestId, sellerUserId, amount, bizId);
        walletAccountService.requireUserWalletActive(sellerUserId);
        WalletTxnResult result = walletLedgerService.post(
                requestId,
                WalletTxnType.ORDER_RELEASE,
                List.of(
                        WalletPosting.debit(walletAccountService.ensureSystemAccount(ESCROW_ACCOUNT_TYPE), amount),
                        WalletPosting.credit(walletAccountService.ensureUserWallet(sellerUserId), amount)
                )
        );
        return new WalletMarketTxnView(result.txnId(), WalletTxnType.ORDER_RELEASE.name(), result.status(), amount, bizId);
    }

    @Transactional
    @Override
    public WalletMarketTxnView refundOrder(String requestId, UUID buyerUserId, long amount, String bizId) {
        validateRequest(requestId, buyerUserId, amount, bizId);
        walletAccountService.requireUserWalletActive(buyerUserId);
        WalletTxnResult result = walletLedgerService.post(
                requestId,
                WalletTxnType.ORDER_REFUND,
                List.of(
                        WalletPosting.debit(walletAccountService.ensureSystemAccount(ESCROW_ACCOUNT_TYPE), amount),
                        WalletPosting.credit(walletAccountService.ensureUserWallet(buyerUserId), amount)
                )
        );
        return new WalletMarketTxnView(result.txnId(), WalletTxnType.ORDER_REFUND.name(), result.status(), amount, bizId);
    }

    private void validateRequest(String requestId, UUID userId, long amount, String bizId) {
        if (!StringUtils.hasText(requestId)) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "requestId must not be blank");
        }
        if (userId == null) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "userId must not be null");
        }
        if (amount <= 0) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "amount must be positive");
        }
        if (!StringUtils.hasText(bizId)) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "bizId must not be blank");
        }
    }
}
