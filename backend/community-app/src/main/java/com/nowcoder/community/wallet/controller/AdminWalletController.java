package com.nowcoder.community.wallet.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.wallet.application.AdminWalletApplicationService;
import com.nowcoder.community.wallet.application.command.AdminFreezeWalletCommand;
import com.nowcoder.community.wallet.application.command.AdminReverseTxnCommand;
import com.nowcoder.community.wallet.controller.dto.AdminFreezeWalletRequest;
import com.nowcoder.community.wallet.controller.dto.AdminReverseTxnRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/wallet/admin")
public class AdminWalletController {

    private final AdminWalletApplicationService adminWalletApplicationService;

    public AdminWalletController(AdminWalletApplicationService adminWalletApplicationService) {
        this.adminWalletApplicationService = adminWalletApplicationService;
    }

    @PostMapping("/freeze")
    public Result<Void> freeze(Authentication authentication, @RequestBody @Valid AdminFreezeWalletRequest request) {
        UUID actorUserId = CurrentUser.requireUserUuid(authentication);
        adminWalletApplicationService.freezeWallet(new AdminFreezeWalletCommand(actorUserId, request.getUserId(), request.getReason()));
        return Result.ok();
    }

    @PostMapping("/reverse")
    public Result<Void> reverse(Authentication authentication, @RequestBody @Valid AdminReverseTxnRequest request) {
        UUID actorUserId = CurrentUser.requireUserUuid(authentication);
        adminWalletApplicationService.reverseTxn(new AdminReverseTxnCommand(actorUserId, request.getTxnRef(), request.getReason()));
        return Result.ok();
    }
}
