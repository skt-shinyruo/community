package com.nowcoder.community.wallet.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.dto.CreateWithdrawResponse;
import com.nowcoder.community.wallet.entity.WithdrawOrder;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import com.nowcoder.community.wallet.mapper.WithdrawOrderMapper;
import com.nowcoder.community.wallet.model.WalletPosting;
import com.nowcoder.community.wallet.model.WalletTxnType;
import org.springframework.dao.DataIntegrityViolationException;
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
        WithdrawOrder order = withdrawOrderMapper.selectByRequestId(requestId);
        if (order != null) {
            ensureReplayMatches(order, userId, amount);
            if ("SUCCEEDED".equals(order.getStatus())) {
                return CreateWithdrawResponse.from(order);
            }
        }

        if (order == null && accountService.balanceOfSystem("PLATFORM_CASH") < amount) {
            order = withdrawOrderMapper.selectByRequestId(requestId);
            if (order == null) {
                throw new BusinessException(WalletErrorCode.PLATFORM_CASH_INSUFFICIENT, "platform cash insufficient");
            }
        }

        order = order == null ? createOrLoad(requestId, userId, amount) : order;
        ensureReplayMatches(order, userId, amount);
        if ("SUCCEEDED".equals(order.getStatus())) {
            return CreateWithdrawResponse.from(order);
        }

        if ("REQUESTED".equals(order.getStatus())) {
            ledgerService.post(
                    requestId + ":request",
                    WalletTxnType.WITHDRAW,
                    List.of(
                            WalletPosting.debit(accountService.ensureUserWallet(userId), amount),
                            WalletPosting.credit(accountService.ensureSystemAccount("WITHDRAW_PENDING"), amount)
                    )
            );
            withdrawOrderMapper.updateStatus(requestId, "REQUESTED", "PROCESSING");
            order = requireOrder(requestId);
        }

        if ("PROCESSING".equals(order.getStatus())) {
            ledgerService.post(
                    requestId + ":settle",
                    WalletTxnType.WITHDRAW,
                    List.of(
                            WalletPosting.debit(accountService.ensureSystemAccount("WITHDRAW_PENDING"), amount),
                            WalletPosting.credit(accountService.ensureSystemAccount("PLATFORM_CASH"), amount)
                    )
            );
            withdrawOrderMapper.updateStatus(requestId, "PROCESSING", "SUCCEEDED");
        }
        return CreateWithdrawResponse.from(requireOrder(requestId));
    }

    private void validate(String requestId, long amount) {
        if (requestId == null || requestId.isBlank()) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "requestId must not be blank");
        }
        if (amount <= 0) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "withdraw amount must be positive");
        }
    }

    private WithdrawOrder createOrLoad(String requestId, int userId, long amount) {
        WithdrawOrder order = new WithdrawOrder();
        order.setRequestId(requestId);
        order.setUserId(userId);
        order.setAmount(amount);
        order.setStatus("REQUESTED");
        try {
            withdrawOrderMapper.insert(order);
            return order;
        } catch (DataIntegrityViolationException ex) {
            WithdrawOrder duplicated = withdrawOrderMapper.selectByRequestId(requestId);
            if (duplicated != null) {
                return duplicated;
            }
            throw ex;
        }
    }

    private WithdrawOrder requireOrder(String requestId) {
        WithdrawOrder order = withdrawOrderMapper.selectByRequestId(requestId);
        if (order == null) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "withdraw order not found: requestId=" + requestId);
        }
        return order;
    }

    private void ensureReplayMatches(WithdrawOrder order, int userId, long amount) {
        if (order.getUserId() != userId || order.getAmount() != amount) {
            throw new BusinessException(
                    WalletErrorCode.REQUEST_REPLAY_CONFLICT,
                    "requestId replay conflict: requestId=" + order.getRequestId()
            );
        }
    }
}
