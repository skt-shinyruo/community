package com.nowcoder.community.wallet.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.wallet.dto.AdminFreezeWalletRequest;
import com.nowcoder.community.wallet.dto.AdminReverseTxnRequest;
import com.nowcoder.community.wallet.service.AdminWalletOpsService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet/admin")
public class AdminWalletController {

    private final AdminWalletOpsService adminWalletOpsService;

    public AdminWalletController(AdminWalletOpsService adminWalletOpsService) {
        this.adminWalletOpsService = adminWalletOpsService;
    }

    @PostMapping("/freeze")
    public Result<Void> freeze(Authentication authentication, @RequestBody @Valid AdminFreezeWalletRequest request) {
        int actorUserId = CurrentUser.requireUserId(authentication);
        adminWalletOpsService.freezeWallet(actorUserId, request.getUserId(), request.getReason());
        return Result.ok();
    }

    @PostMapping("/reverse")
    public Result<Void> reverse(Authentication authentication, @RequestBody @Valid AdminReverseTxnRequest request) {
        int actorUserId = CurrentUser.requireUserId(authentication);
        adminWalletOpsService.reverseTxn(actorUserId, request.getTxnRef(), request.getReason());
        return Result.ok();
    }
}
