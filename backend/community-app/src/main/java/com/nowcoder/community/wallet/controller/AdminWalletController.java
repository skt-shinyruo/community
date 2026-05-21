package com.nowcoder.community.wallet.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.wallet.application.command.AdminFreezeWalletCommand;
import com.nowcoder.community.wallet.application.command.AdminReverseTxnCommand;
import com.nowcoder.community.wallet.application.WalletAdminOpsApplicationService;
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

    private final WalletAdminOpsApplicationService walletAdminOpsApplicationService;

    public AdminWalletController(WalletAdminOpsApplicationService walletAdminOpsApplicationService) {
        this.walletAdminOpsApplicationService = walletAdminOpsApplicationService;
    }

    @PostMapping("/freeze")
    public Result<Void> freeze(Authentication authentication, @RequestBody @Valid AdminFreezeWalletRequest request) {
        UUID actorUserId = CurrentUser.requireUserUuid(authentication);
        AdminFreezeWalletCommand command = new AdminFreezeWalletCommand(actorUserId, request.getUserId(), request.getReason());
        walletAdminOpsApplicationService.freezeWallet(command.actorUserId(), command.userId(), command.reason());
        return Result.ok();
    }

    @PostMapping("/reverse")
    public Result<Void> reverse(Authentication authentication, @RequestBody @Valid AdminReverseTxnRequest request) {
        UUID actorUserId = CurrentUser.requireUserUuid(authentication);
        AdminReverseTxnCommand command = new AdminReverseTxnCommand(actorUserId, request.getTxnRef(), request.getReason());
        walletAdminOpsApplicationService.reverseTxn(command.actorUserId(), command.txnRef(), command.reason());
        return Result.ok();
    }
}
