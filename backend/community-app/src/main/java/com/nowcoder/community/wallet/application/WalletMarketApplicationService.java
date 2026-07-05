package com.nowcoder.community.wallet.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.application.command.WalletMarketTxnCommand;
import com.nowcoder.community.wallet.application.result.WalletMarketTxnResult;
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
public class WalletMarketApplicationService {

    private static final String ESCROW_ACCOUNT_TYPE = "ORDER_ESCROW";

    private final WalletAccountApplicationService walletAccountService;
    private final WalletLedgerApplicationService walletLedgerService;

    public WalletMarketApplicationService(WalletAccountApplicationService walletAccountService,
                                          WalletLedgerApplicationService walletLedgerService) {
        this.walletAccountService = walletAccountService;
        this.walletLedgerService = walletLedgerService;
    }

    @Transactional
    public WalletMarketTxnResult escrowOrder(WalletMarketTxnCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        validateRequest(command.requestId(), command.userId(), command.amount(), command.bizId());
        walletAccountService.requireUserWalletActive(command.userId());
        WalletTxnResult result = walletLedgerService.post(new WalletLedgerCommand(
                command.requestId(),
                WalletTxnType.ORDER_ESCROW,
                WalletTxnType.ORDER_ESCROW.name(),
                command.bizId(),
                List.of(
                        WalletPosting.debit(walletAccountService.ensureUserWallet(command.userId()), command.amount()),
                        WalletPosting.credit(walletAccountService.ensureSystemAccount(ESCROW_ACCOUNT_TYPE), command.amount())
                )
        ));
        return new WalletMarketTxnResult(result.txnId(), WalletTxnType.ORDER_ESCROW.name(), result.status(), command.amount(), command.bizId());
    }

    @Transactional
    public WalletMarketTxnResult releaseOrder(WalletMarketTxnCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        validateRequest(command.requestId(), command.userId(), command.amount(), command.bizId());
        WalletTxnResult result = walletLedgerService.post(new WalletLedgerCommand(
                command.requestId(),
                WalletTxnType.ORDER_RELEASE,
                WalletTxnType.ORDER_RELEASE.name(),
                command.bizId(),
                List.of(
                        WalletPosting.debit(walletAccountService.ensureSystemAccount(ESCROW_ACCOUNT_TYPE), command.amount()),
                        WalletPosting.credit(walletAccountService.ensureUserWallet(command.userId()), command.amount())
                )
        ));
        return new WalletMarketTxnResult(result.txnId(), WalletTxnType.ORDER_RELEASE.name(), result.status(), command.amount(), command.bizId());
    }

    @Transactional
    public WalletMarketTxnResult refundOrder(WalletMarketTxnCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        validateRequest(command.requestId(), command.userId(), command.amount(), command.bizId());
        WalletTxnResult result = walletLedgerService.post(new WalletLedgerCommand(
                command.requestId(),
                WalletTxnType.ORDER_REFUND,
                WalletTxnType.ORDER_REFUND.name(),
                command.bizId(),
                List.of(
                        WalletPosting.debit(walletAccountService.ensureSystemAccount(ESCROW_ACCOUNT_TYPE), command.amount()),
                        WalletPosting.credit(walletAccountService.ensureUserWallet(command.userId()), command.amount())
                )
        ));
        return new WalletMarketTxnResult(result.txnId(), WalletTxnType.ORDER_REFUND.name(), result.status(), command.amount(), command.bizId());
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
