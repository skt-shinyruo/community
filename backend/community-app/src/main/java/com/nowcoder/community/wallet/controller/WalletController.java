package com.nowcoder.community.wallet.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.wallet.dto.CreateRechargeRequest;
import com.nowcoder.community.wallet.dto.CreateRechargeResponse;
import com.nowcoder.community.wallet.dto.CreateTransferRequest;
import com.nowcoder.community.wallet.dto.CreateTransferResponse;
import com.nowcoder.community.wallet.dto.CreateWithdrawRequest;
import com.nowcoder.community.wallet.dto.CreateWithdrawResponse;
import com.nowcoder.community.wallet.dto.WalletSummaryResponse;
import com.nowcoder.community.wallet.service.RechargeService;
import com.nowcoder.community.wallet.service.TransferService;
import com.nowcoder.community.wallet.service.WalletQueryService;
import com.nowcoder.community.wallet.service.WithdrawService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletQueryService walletQueryService;
    private final RechargeService rechargeService;
    private final WithdrawService withdrawService;
    private final TransferService transferService;

    public WalletController(WalletQueryService walletQueryService,
                            RechargeService rechargeService,
                            WithdrawService withdrawService,
                            TransferService transferService) {
        this.walletQueryService = walletQueryService;
        this.rechargeService = rechargeService;
        this.withdrawService = withdrawService;
        this.transferService = transferService;
    }

    @GetMapping("/summary")
    public Result<WalletSummaryResponse> summary(Authentication authentication) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(walletQueryService.summary(userId));
    }

    @PostMapping("/recharges")
    public Result<CreateRechargeResponse> recharge(Authentication authentication, @RequestBody @Valid CreateRechargeRequest request) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(rechargeService.complete(request.getRequestId(), userId, request.getAmount()));
    }

    @PostMapping("/withdrawals")
    public Result<CreateWithdrawResponse> withdraw(Authentication authentication, @RequestBody @Valid CreateWithdrawRequest request) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(withdrawService.request(request.getRequestId(), userId, request.getAmount()));
    }

    @PostMapping("/transfers")
    public Result<CreateTransferResponse> transfer(Authentication authentication, @RequestBody @Valid CreateTransferRequest request) {
        UUID fromUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(transferService.create(
                request.getRequestId(),
                fromUserId,
                request.getToUserId(),
                request.getAmount()
        ));
    }
}
