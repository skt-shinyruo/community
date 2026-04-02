package com.nowcoder.community.wallet.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.wallet.dto.CreateRechargeRequest;
import com.nowcoder.community.wallet.dto.CreateRechargeResponse;
import com.nowcoder.community.wallet.dto.CreateWithdrawRequest;
import com.nowcoder.community.wallet.dto.CreateWithdrawResponse;
import com.nowcoder.community.wallet.dto.WalletSummaryResponse;
import com.nowcoder.community.wallet.service.RechargeService;
import com.nowcoder.community.wallet.service.WalletQueryService;
import com.nowcoder.community.wallet.service.WithdrawService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletQueryService walletQueryService;
    private final RechargeService rechargeService;
    private final WithdrawService withdrawService;

    public WalletController(WalletQueryService walletQueryService,
                            RechargeService rechargeService,
                            WithdrawService withdrawService) {
        this.walletQueryService = walletQueryService;
        this.rechargeService = rechargeService;
        this.withdrawService = withdrawService;
    }

    @GetMapping("/summary")
    public Result<WalletSummaryResponse> summary(Authentication authentication) {
        int userId = CurrentUser.requireUserId(authentication);
        return Result.ok(walletQueryService.summary(userId));
    }

    @PostMapping("/recharges")
    public Result<CreateRechargeResponse> recharge(Authentication authentication, @RequestBody @Valid CreateRechargeRequest request) {
        int userId = CurrentUser.requireUserId(authentication);
        return Result.ok(rechargeService.complete(request.getRequestId(), userId, request.getAmount()));
    }

    @PostMapping("/withdrawals")
    public Result<CreateWithdrawResponse> withdraw(Authentication authentication, @RequestBody @Valid CreateWithdrawRequest request) {
        int userId = CurrentUser.requireUserId(authentication);
        return Result.ok(withdrawService.request(request.getRequestId(), userId, request.getAmount()));
    }
}
