package com.nowcoder.community.wallet.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.wallet.application.WalletAccountApplicationService;
import com.nowcoder.community.wallet.application.WalletLedgerApplicationService;
import com.nowcoder.community.wallet.application.WalletRechargeApplicationService;
import com.nowcoder.community.wallet.application.WalletTestCreditCapabilityApplicationService;
import com.nowcoder.community.wallet.application.WalletTransferApplicationService;
import com.nowcoder.community.wallet.application.WalletWithdrawApplicationService;
import com.nowcoder.community.wallet.application.WalletAccountApplicationService.WalletSummaryResult;
import com.nowcoder.community.wallet.application.WalletLedgerApplicationService.ListWalletTransactionsCommand;
import com.nowcoder.community.wallet.application.WalletRechargeApplicationService.CreateRechargeCommand;
import com.nowcoder.community.wallet.application.WalletRechargeApplicationService.RechargeOrderResult;
import com.nowcoder.community.wallet.application.WalletTestCreditCapabilityApplicationService.WalletCapabilitiesResult;
import com.nowcoder.community.wallet.application.WalletTransferApplicationService.CreateTransferCommand;
import com.nowcoder.community.wallet.application.WalletTransferApplicationService.TransferOrderResult;
import com.nowcoder.community.wallet.application.WalletWithdrawApplicationService.CreateWithdrawCommand;
import com.nowcoder.community.wallet.application.WalletWithdrawApplicationService.WithdrawOrderResult;
import com.nowcoder.community.wallet.application.result.WalletTransactionResult;
import com.nowcoder.community.wallet.controller.dto.CreateRechargeRequest;
import com.nowcoder.community.wallet.controller.dto.CreateTransferRequest;
import com.nowcoder.community.wallet.controller.dto.CreateWithdrawRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletAccountApplicationService accountService;
    private final WalletLedgerApplicationService ledgerService;
    private final WalletRechargeApplicationService rechargeService;
    private final WalletWithdrawApplicationService withdrawService;
    private final WalletTransferApplicationService transferService;
    private final WalletTestCreditCapabilityApplicationService capabilityService;

    public WalletController(
            WalletAccountApplicationService accountService,
            WalletLedgerApplicationService ledgerService,
            WalletRechargeApplicationService rechargeService,
            WalletWithdrawApplicationService withdrawService,
            WalletTransferApplicationService transferService,
            WalletTestCreditCapabilityApplicationService capabilityService
    ) {
        this.accountService = accountService;
        this.ledgerService = ledgerService;
        this.rechargeService = rechargeService;
        this.withdrawService = withdrawService;
        this.transferService = transferService;
        this.capabilityService = capabilityService;
    }

    @GetMapping("/summary")
    public Result<WalletSummaryResult> summary(Authentication authentication) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(accountService.summary(userId));
    }

    @GetMapping("/capabilities")
    public Result<WalletCapabilitiesResult> capabilities(Authentication authentication) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(capabilityService.capabilities(userId));
    }

    @GetMapping("/transactions")
    public Result<List<WalletTransactionResult>> transactions(
            Authentication authentication,
            @RequestParam(required = false) Integer limit
    ) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(ledgerService.recentTransactions(new ListWalletTransactionsCommand(userId, limit)));
    }

    @PostMapping("/recharges")
    public Result<RechargeOrderResult> recharge(
            Authentication authentication,
            @RequestHeader(value = IdempotencyGuard.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody @Valid CreateRechargeRequest request
    ) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(rechargeService.recharge(
                new CreateRechargeCommand(userId, request.getAmount(), idempotencyKey)
        ));
    }

    @PostMapping("/withdrawals")
    public Result<WithdrawOrderResult> withdraw(
            Authentication authentication,
            @RequestHeader(value = IdempotencyGuard.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody @Valid CreateWithdrawRequest request
    ) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(withdrawService.withdraw(
                new CreateWithdrawCommand(userId, request.getAmount(), idempotencyKey)
        ));
    }

    @PostMapping("/transfers")
    public Result<TransferOrderResult> transfer(
            Authentication authentication,
            @RequestHeader(value = IdempotencyGuard.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody @Valid CreateTransferRequest request
    ) {
        UUID fromUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(transferService.transfer(
                new CreateTransferCommand(fromUserId, request.getToUserId(), request.getAmount(), idempotencyKey)
        ));
    }
}
