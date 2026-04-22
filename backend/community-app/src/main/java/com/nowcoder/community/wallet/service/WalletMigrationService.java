package com.nowcoder.community.wallet.service;

import com.nowcoder.community.growth.api.model.LegacyRewardAccountView;
import com.nowcoder.community.growth.api.query.LegacyRewardAccountQueryApi;
import com.nowcoder.community.wallet.model.WalletPosting;
import com.nowcoder.community.wallet.model.WalletTxnType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class WalletMigrationService {

    private final LegacyRewardAccountQueryApi legacyRewardAccountQueryApi;
    private final WalletAccountService walletAccountService;
    private final WalletLedgerService walletLedgerService;

    public WalletMigrationService(LegacyRewardAccountQueryApi legacyRewardAccountQueryApi,
                                  WalletAccountService walletAccountService,
                                  WalletLedgerService walletLedgerService) {
        this.legacyRewardAccountQueryApi = legacyRewardAccountQueryApi;
        this.walletAccountService = walletAccountService;
        this.walletLedgerService = walletLedgerService;
    }

    @Transactional
    public void migrateUser(UUID userId) {
        UUID userWalletId = walletAccountService.ensureUserWallet(userId);
        LegacyRewardAccountView legacy = legacyRewardAccountQueryApi.getLegacyRewardAccount(userId);
        if (legacy == null) {
            return;
        }

        if (legacy.availableBalance() > 0) {
            walletLedgerService.post(
                    "migration:opening:" + userId,
                    WalletTxnType.OPENING_BALANCE,
                    List.of(
                            WalletPosting.debit(walletAccountService.ensureSystemAccount("MIGRATION_HOLD"), legacy.availableBalance()),
                            WalletPosting.credit(userWalletId, legacy.availableBalance())
                    )
            );
        }

        if (legacy.frozenBalance() > 0) {
            walletLedgerService.post(
                    "migration:frozen:" + userId,
                    WalletTxnType.FREEZE,
                    List.of(
                            WalletPosting.debit(walletAccountService.ensureSystemAccount("MIGRATION_HOLD"), legacy.frozenBalance()),
                            WalletPosting.credit(walletAccountService.ensureSystemAccount("RISK_FROZEN"), legacy.frozenBalance())
                    )
            );
        }
    }
}
