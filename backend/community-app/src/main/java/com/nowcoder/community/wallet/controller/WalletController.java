package com.nowcoder.community.wallet.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.wallet.application.WalletAccountApplicationService;
import com.nowcoder.community.wallet.application.WalletLedgerApplicationService;
import com.nowcoder.community.wallet.application.WalletRechargeApplicationService;
import com.nowcoder.community.wallet.application.WalletTransferApplicationService;
import com.nowcoder.community.wallet.application.WalletWithdrawApplicationService;
import com.nowcoder.community.wallet.application.command.CreateRechargeCommand;
import com.nowcoder.community.wallet.application.command.CreateTransferCommand;
import com.nowcoder.community.wallet.application.command.CreateWithdrawCommand;
import com.nowcoder.community.wallet.application.command.ListWalletTransactionsCommand;
import com.nowcoder.community.wallet.controller.dto.CreateRechargeRequest;
import com.nowcoder.community.wallet.controller.dto.CreateRechargeResponse;
import com.nowcoder.community.wallet.controller.dto.CreateTransferRequest;
import com.nowcoder.community.wallet.controller.dto.CreateTransferResponse;
import com.nowcoder.community.wallet.controller.dto.CreateWithdrawRequest;
import com.nowcoder.community.wallet.controller.dto.CreateWithdrawResponse;
import com.nowcoder.community.wallet.controller.dto.WalletSummaryResponse;
import com.nowcoder.community.wallet.controller.dto.WalletTransactionResponse;
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

    public WalletController(
            WalletAccountApplicationService accountService,
            WalletLedgerApplicationService ledgerService,
            WalletRechargeApplicationService rechargeService,
            WalletWithdrawApplicationService withdrawService,
            WalletTransferApplicationService transferService
    ) {
        this.accountService = accountService;
        this.ledgerService = ledgerService;
        this.rechargeService = rechargeService;
        this.withdrawService = withdrawService;
        this.transferService = transferService;
    }

    @GetMapping("/summary")
    public Result<WalletSummaryResponse> summary(Authentication authentication) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(WalletSummaryResponse.from(accountService.summary(userId)));
    }

    @GetMapping("/transactions")
    public Result<List<WalletTransactionResponse>> transactions(
            Authentication authentication,
            @RequestParam(required = false) Integer limit
    ) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(ledgerService.recentTransactions(new ListWalletTransactionsCommand(userId, limit))
                .stream()
                .map(WalletTransactionResponse::from)
                .toList());
    }

    @PostMapping("/recharges")
    public Result<CreateRechargeResponse> recharge(
            Authentication authentication,
            @RequestHeader(value = IdempotencyGuard.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody @Valid CreateRechargeRequest request
    ) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(CreateRechargeResponse.from(rechargeService.recharge(
                new CreateRechargeCommand(userId, request.getAmount(), idempotencyKey)
        )));
    }

    @PostMapping("/withdrawals")
    public Result<CreateWithdrawResponse> withdraw(
            Authentication authentication,
            @RequestHeader(value = IdempotencyGuard.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody @Valid CreateWithdrawRequest request
    ) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(CreateWithdrawResponse.from(withdrawService.withdraw(
                new CreateWithdrawCommand(userId, request.getAmount(), idempotencyKey)
        )));
    }

    @PostMapping("/transfers")
    public Result<CreateTransferResponse> transfer(
            Authentication authentication,
            @RequestHeader(value = IdempotencyGuard.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody @Valid CreateTransferRequest request
    ) {
        UUID fromUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(CreateTransferResponse.from(transferService.transfer(
                new CreateTransferCommand(fromUserId, request.getToUserId(), request.getAmount(), idempotencyKey)
        )));
    }
}
