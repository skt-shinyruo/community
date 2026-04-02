package com.nowcoder.community.wallet.service;

import com.nowcoder.community.growth.entity.RewardAccount;
import com.nowcoder.community.growth.mapper.RewardAccountMapper;
import com.nowcoder.community.wallet.model.WalletPosting;
import com.nowcoder.community.wallet.model.WalletTxnType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WalletMigrationService {

    private final RewardAccountMapper rewardAccountMapper;
    private final WalletAccountService walletAccountService;
    private final WalletLedgerService walletLedgerService;

    public WalletMigrationService(RewardAccountMapper rewardAccountMapper,
                                  WalletAccountService walletAccountService,
                                  WalletLedgerService walletLedgerService) {
        this.rewardAccountMapper = rewardAccountMapper;
        this.walletAccountService = walletAccountService;
        this.walletLedgerService = walletLedgerService;
    }

    @Transactional
    public void migrateUser(int userId) {
        long userWalletId = walletAccountService.ensureUserWallet(userId);
        RewardAccount legacy = rewardAccountMapper.selectByUserId(userId);
        if (legacy == null) {
            return;
        }

        if (legacy.getAvailableBalance() > 0) {
            walletLedgerService.post(
                    "migration:opening:" + userId,
                    WalletTxnType.OPENING_BALANCE,
                    List.of(
                            WalletPosting.debit(walletAccountService.ensureSystemAccount("MIGRATION_HOLD"), legacy.getAvailableBalance()),
                            WalletPosting.credit(userWalletId, legacy.getAvailableBalance())
                    )
            );
        }

        if (legacy.getFrozenBalance() > 0) {
            walletLedgerService.post(
                    "migration:frozen:" + userId,
                    WalletTxnType.FREEZE,
                    List.of(
                            WalletPosting.debit(walletAccountService.ensureSystemAccount("MIGRATION_HOLD"), legacy.getFrozenBalance()),
                            WalletPosting.credit(walletAccountService.ensureSystemAccount("RISK_FROZEN"), legacy.getFrozenBalance())
                    )
            );
        }
    }
}
