package com.nowcoder.community.wallet.application;

import org.springframework.stereotype.Service;

import com.nowcoder.community.wallet.application.command.AdminFreezeWalletCommand;
import com.nowcoder.community.wallet.application.command.AdminReverseTxnCommand;

@Service
public class AdminWalletApplicationService {

    private final WalletAdminOpsApplicationService adminWalletOpsService;

    public AdminWalletApplicationService(WalletAdminOpsApplicationService adminWalletOpsService) {
        this.adminWalletOpsService = adminWalletOpsService;
    }

    public void freezeWallet(AdminFreezeWalletCommand command) {
        adminWalletOpsService.freezeWallet(command.actorUserId(), command.userId(), command.reason());
    }

    public void reverseTxn(AdminReverseTxnCommand command) {
        adminWalletOpsService.reverseTxn(command.actorUserId(), command.txnRef(), command.reason());
    }
}
