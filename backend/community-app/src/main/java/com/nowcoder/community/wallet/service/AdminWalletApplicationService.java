package com.nowcoder.community.wallet.service;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AdminWalletApplicationService {

    private final AdminWalletOpsService adminWalletOpsService;

    public AdminWalletApplicationService(AdminWalletOpsService adminWalletOpsService) {
        this.adminWalletOpsService = adminWalletOpsService;
    }

    public void freezeWallet(UUID actorUserId, UUID userId, String reason) {
        adminWalletOpsService.freezeWallet(actorUserId, userId, reason);
    }

    public void reverseTxn(UUID actorUserId, String txnRef, String reason) {
        adminWalletOpsService.reverseTxn(actorUserId, txnRef, reason);
    }
}
