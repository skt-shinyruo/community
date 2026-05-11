package com.nowcoder.community.wallet.application;

import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.infra.idempotency.EffectiveIdempotencyKey;
import com.nowcoder.community.infra.idempotency.IdempotencyKeyResolver;
import com.nowcoder.community.infra.idempotency.RequestFingerprint;
import com.nowcoder.community.wallet.application.command.CreateRechargeCommand;
import com.nowcoder.community.wallet.application.command.CreateTransferCommand;
import com.nowcoder.community.wallet.application.command.CreateWithdrawCommand;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import com.nowcoder.community.wallet.application.result.RechargeOrderResult;
import com.nowcoder.community.wallet.application.result.TransferOrderResult;
import com.nowcoder.community.wallet.application.result.WalletSummaryResult;
import com.nowcoder.community.wallet.application.result.WithdrawOrderResult;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class WalletApplicationService {

    private final WalletAccountApplicationService accountService;
    private final WalletRechargeApplicationService rechargeApplicationService;
    private final WalletWithdrawApplicationService withdrawApplicationService;
    private final WalletTransferApplicationService transferApplicationService;
    private final IdempotencyGuard idempotencyGuard;

    public WalletApplicationService(
            WalletAccountApplicationService accountService,
            WalletRechargeApplicationService rechargeApplicationService,
            WalletWithdrawApplicationService withdrawApplicationService,
            WalletTransferApplicationService transferApplicationService,
            IdempotencyGuard idempotencyGuard
    ) {
        this.accountService = accountService;
        this.rechargeApplicationService = rechargeApplicationService;
        this.withdrawApplicationService = withdrawApplicationService;
        this.transferApplicationService = transferApplicationService;
        this.idempotencyGuard = idempotencyGuard;
    }

    public WalletSummaryResult summary(UUID userId) {
        return new WalletSummaryResult(userId, accountService.balanceOfUser(userId), accountService.statusOfUser(userId));
    }

    public RechargeOrderResult recharge(CreateRechargeCommand command) {
        EffectiveIdempotencyKey effective = IdempotencyKeyResolver.resolve(command.idempotencyKey());
        return idempotencyGuard.executeRequired(
                "wallet:recharge",
                command.userId(),
                effective.value(),
                RequestFingerprint.sha256("wallet:recharge|amount=" + command.amount()),
                WalletErrorCode.REQUEST_REPLAY_CONFLICT,
                RechargeOrderResult.class,
                () -> rechargeApplicationService.complete(effective.value(), command.userId(), command.amount())
        );
    }

    public WithdrawOrderResult withdraw(CreateWithdrawCommand command) {
        EffectiveIdempotencyKey effective = IdempotencyKeyResolver.resolve(command.idempotencyKey());
        return idempotencyGuard.executeRequired(
                "wallet:withdraw",
                command.userId(),
                effective.value(),
                RequestFingerprint.sha256("wallet:withdraw|amount=" + command.amount()),
                WalletErrorCode.REQUEST_REPLAY_CONFLICT,
                WithdrawOrderResult.class,
                () -> withdrawApplicationService.request(effective.value(), command.userId(), command.amount())
        );
    }

    public TransferOrderResult transfer(CreateTransferCommand command) {
        EffectiveIdempotencyKey effective = IdempotencyKeyResolver.resolve(command.idempotencyKey());
        String requestHash = RequestFingerprint.sha256(
                "wallet:transfer|toUserId=" + command.toUserId() + "|amount=" + command.amount()
        );
        return idempotencyGuard.executeRequired(
                "wallet:transfer",
                command.fromUserId(),
                effective.value(),
                requestHash,
                WalletErrorCode.REQUEST_REPLAY_CONFLICT,
                TransferOrderResult.class,
                () -> transferApplicationService.create(
                        effective.value(),
                        command.fromUserId(),
                        command.toUserId(),
                        command.amount()
                )
        );
    }

}
