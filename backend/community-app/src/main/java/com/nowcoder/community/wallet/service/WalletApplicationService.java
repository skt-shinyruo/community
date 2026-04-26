package com.nowcoder.community.wallet.service;

import com.nowcoder.community.wallet.dto.CreateRechargeRequest;
import com.nowcoder.community.wallet.dto.CreateTransferRequest;
import com.nowcoder.community.wallet.dto.CreateWithdrawRequest;
import com.nowcoder.community.wallet.dto.WalletSummaryResponse;
import com.nowcoder.community.wallet.model.RechargeOrderResult;
import com.nowcoder.community.wallet.model.TransferOrderResult;
import com.nowcoder.community.wallet.model.WithdrawOrderResult;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class WalletApplicationService {

    private final WalletQueryService walletQueryService;
    private final RechargeService rechargeService;
    private final WithdrawService withdrawService;
    private final TransferService transferService;

    public WalletApplicationService(
            WalletQueryService walletQueryService,
            RechargeService rechargeService,
            WithdrawService withdrawService,
            TransferService transferService
    ) {
        this.walletQueryService = walletQueryService;
        this.rechargeService = rechargeService;
        this.withdrawService = withdrawService;
        this.transferService = transferService;
    }

    public WalletSummaryResponse summary(UUID userId) {
        return walletQueryService.summary(userId);
    }

    public RechargeOrderResult recharge(UUID userId, CreateRechargeRequest request) {
        return rechargeService.complete(request.getRequestId(), userId, request.getAmount());
    }

    public WithdrawOrderResult withdraw(UUID userId, CreateWithdrawRequest request) {
        return withdrawService.request(request.getRequestId(), userId, request.getAmount());
    }

    public TransferOrderResult transfer(UUID fromUserId, CreateTransferRequest request) {
        return transferService.create(
                request.getRequestId(),
                fromUserId,
                request.getToUserId(),
                request.getAmount()
        );
    }
}
