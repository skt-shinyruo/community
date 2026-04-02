package com.nowcoder.community.wallet.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.dto.CreateWithdrawResponse;
import com.nowcoder.community.wallet.entity.WithdrawOrder;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import com.nowcoder.community.wallet.mapper.WithdrawOrderMapper;
import com.nowcoder.community.wallet.model.WalletPosting;
import com.nowcoder.community.wallet.model.WalletTxnType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WithdrawService {

    private final WithdrawOrderMapper withdrawOrderMapper;
    private final WalletAccountService accountService;
    private final WalletLedgerService ledgerService;

    public WithdrawService(WithdrawOrderMapper withdrawOrderMapper,
                           WalletAccountService accountService,
                           WalletLedgerService ledgerService) {
        this.withdrawOrderMapper = withdrawOrderMapper;
        this.accountService = accountService;
        this.ledgerService = ledgerService;
    }

    @Transactional
    public CreateWithdrawResponse request(String requestId, int userId, long amount) {
        validate(requestId, amount);
        if (accountService.balanceOfSystem("PLATFORM_CASH") < amount) {
            throw new BusinessException(WalletErrorCode.PLATFORM_CASH_INSUFFICIENT, "platform cash insufficient");
        }

        WithdrawOrder existing = withdrawOrderMapper.selectByRequestId(requestId);
        if (existing == null) {
            WithdrawOrder order = new WithdrawOrder();
            order.setRequestId(requestId);
            order.setUserId(userId);
            order.setAmount(amount);
            order.setStatus("REQUESTED");
            withdrawOrderMapper.insert(order);
        }

        ledgerService.post(
                requestId + ":request",
                WalletTxnType.WITHDRAW,
                List.of(
                        WalletPosting.debit(accountService.ensureUserWallet(userId), amount),
                        WalletPosting.credit(accountService.ensureSystemAccount("WITHDRAW_PENDING"), amount)
                )
        );
        withdrawOrderMapper.updateStatus(requestId, "REQUESTED", "PROCESSING");
        ledgerService.post(
                requestId + ":settle",
                WalletTxnType.WITHDRAW,
                List.of(
                        WalletPosting.debit(accountService.ensureSystemAccount("WITHDRAW_PENDING"), amount),
                        WalletPosting.credit(accountService.ensureSystemAccount("PLATFORM_CASH"), amount)
                )
        );
        withdrawOrderMapper.updateStatus(requestId, "PROCESSING", "SUCCEEDED");
        WithdrawOrder order = withdrawOrderMapper.selectByRequestId(requestId);
        return CreateWithdrawResponse.from(order);
    }

    private void validate(String requestId, long amount) {
        if (requestId == null || requestId.isBlank()) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "requestId must not be blank");
        }
        if (amount <= 0) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "withdraw amount must be positive");
        }
    }
}
