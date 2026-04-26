package com.nowcoder.community.wallet.service;

import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.infra.idempotency.EffectiveIdempotencyKey;
import com.nowcoder.community.infra.idempotency.IdempotencyKeyResolver;
import com.nowcoder.community.infra.idempotency.RequestFingerprint;
import com.nowcoder.community.wallet.dto.CreateRechargeRequest;
import com.nowcoder.community.wallet.dto.CreateTransferRequest;
import com.nowcoder.community.wallet.dto.CreateWithdrawRequest;
import com.nowcoder.community.wallet.dto.WalletSummaryResponse;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
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
    private final IdempotencyGuard idempotencyGuard;

    public WalletApplicationService(
            WalletQueryService walletQueryService,
            RechargeService rechargeService,
            WithdrawService withdrawService,
            TransferService transferService,
            IdempotencyGuard idempotencyGuard
    ) {
        this.walletQueryService = walletQueryService;
        this.rechargeService = rechargeService;
        this.withdrawService = withdrawService;
        this.transferService = transferService;
        this.idempotencyGuard = idempotencyGuard;
    }

    public WalletSummaryResponse summary(UUID userId) {
        return walletQueryService.summary(userId);
    }

    public RechargeOrderResult recharge(UUID userId, CreateRechargeRequest request) {
        return recharge(userId, request, null);
    }

    public RechargeOrderResult recharge(UUID userId, CreateRechargeRequest request, String idempotencyKey) {
        EffectiveIdempotencyKey effective = IdempotencyKeyResolver.resolve(idempotencyKey, request.getRequestId());
        return idempotencyGuard.executeRequired(
                "wallet:recharge",
                userId,
                effective.value(),
                RequestFingerprint.sha256("wallet:recharge|amount=" + request.getAmount()),
                WalletErrorCode.REQUEST_REPLAY_CONFLICT,
                RechargeOrderResult.class,
                () -> rechargeService.complete(effective.value(), userId, request.getAmount())
        );
    }

    public WithdrawOrderResult withdraw(UUID userId, CreateWithdrawRequest request) {
        return withdraw(userId, request, null);
    }

    public WithdrawOrderResult withdraw(UUID userId, CreateWithdrawRequest request, String idempotencyKey) {
        EffectiveIdempotencyKey effective = IdempotencyKeyResolver.resolve(idempotencyKey, request.getRequestId());
        return idempotencyGuard.executeRequired(
                "wallet:withdraw",
                userId,
                effective.value(),
                RequestFingerprint.sha256("wallet:withdraw|amount=" + request.getAmount()),
                WalletErrorCode.REQUEST_REPLAY_CONFLICT,
                WithdrawOrderResult.class,
                () -> withdrawService.request(effective.value(), userId, request.getAmount())
        );
    }

    public TransferOrderResult transfer(UUID fromUserId, CreateTransferRequest request) {
        return transfer(fromUserId, request, null);
    }

    public TransferOrderResult transfer(UUID fromUserId, CreateTransferRequest request, String idempotencyKey) {
        EffectiveIdempotencyKey effective = IdempotencyKeyResolver.resolve(idempotencyKey, request.getRequestId());
        String requestHash = RequestFingerprint.sha256(
                "wallet:transfer|toUserId=" + request.getToUserId() + "|amount=" + request.getAmount()
        );
        return idempotencyGuard.executeRequired(
                "wallet:transfer",
                fromUserId,
                effective.value(),
                requestHash,
                WalletErrorCode.REQUEST_REPLAY_CONFLICT,
                TransferOrderResult.class,
                () -> transferService.create(
                        effective.value(),
                        fromUserId,
                        request.getToUserId(),
                        request.getAmount()
                )
        );
    }

}
