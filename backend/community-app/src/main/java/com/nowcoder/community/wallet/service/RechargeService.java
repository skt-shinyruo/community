package com.nowcoder.community.wallet.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.dto.CreateRechargeResponse;
import com.nowcoder.community.wallet.entity.RechargeOrder;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import com.nowcoder.community.wallet.mapper.RechargeOrderMapper;
import com.nowcoder.community.wallet.model.WalletPosting;
import com.nowcoder.community.wallet.model.WalletTxnType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RechargeService {

    private final RechargeOrderMapper rechargeOrderMapper;
    private final WalletAccountService accountService;
    private final WalletLedgerService ledgerService;

    public RechargeService(RechargeOrderMapper rechargeOrderMapper,
                           WalletAccountService accountService,
                           WalletLedgerService ledgerService) {
        this.rechargeOrderMapper = rechargeOrderMapper;
        this.accountService = accountService;
        this.ledgerService = ledgerService;
    }

    @Transactional
    public CreateRechargeResponse complete(String requestId, int userId, long amount) {
        validate(requestId, amount);

        RechargeOrder existing = rechargeOrderMapper.selectByRequestId(requestId);
        if (existing == null) {
            RechargeOrder order = new RechargeOrder();
            order.setRequestId(requestId);
            order.setUserId(userId);
            order.setAmount(amount);
            order.setStatus("CREATED");
            rechargeOrderMapper.insert(order);
        }

        ledgerService.post(
                requestId,
                WalletTxnType.RECHARGE,
                List.of(
                        WalletPosting.debit(accountService.ensureSystemAccount("PLATFORM_CASH"), amount),
                        WalletPosting.credit(accountService.ensureUserWallet(userId), amount)
                )
        );
        rechargeOrderMapper.updateStatus(requestId, "CREATED", "PAID");
        RechargeOrder order = rechargeOrderMapper.selectByRequestId(requestId);
        return CreateRechargeResponse.from(order);
    }

    private void validate(String requestId, long amount) {
        if (requestId == null || requestId.isBlank()) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "requestId must not be blank");
        }
        if (amount <= 0) {
            throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "recharge amount must be positive");
        }
    }
}
