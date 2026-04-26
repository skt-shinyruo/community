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
import com.nowcoder.community.wallet.service.WalletApplicationService;
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

    private final WalletApplicationService walletApplicationService;

    public WalletController(WalletApplicationService walletApplicationService) {
        this.walletApplicationService = walletApplicationService;
    }

    @GetMapping("/summary")
    public Result<WalletSummaryResponse> summary(Authentication authentication) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(walletApplicationService.summary(userId));
    }

    @PostMapping("/recharges")
    public Result<CreateRechargeResponse> recharge(Authentication authentication, @RequestBody @Valid CreateRechargeRequest request) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(CreateRechargeResponse.from(walletApplicationService.recharge(userId, request)));
    }

    @PostMapping("/withdrawals")
    public Result<CreateWithdrawResponse> withdraw(Authentication authentication, @RequestBody @Valid CreateWithdrawRequest request) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(CreateWithdrawResponse.from(walletApplicationService.withdraw(userId, request)));
    }

    @PostMapping("/transfers")
    public Result<CreateTransferResponse> transfer(Authentication authentication, @RequestBody @Valid CreateTransferRequest request) {
        UUID fromUserId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(CreateTransferResponse.from(walletApplicationService.transfer(fromUserId, request)));
    }
}
