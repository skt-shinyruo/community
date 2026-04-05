package com.nowcoder.community.wallet.service;

import com.nowcoder.community.wallet.dto.WalletSummaryResponse;
import org.springframework.stereotype.Service;

@Service
public class WalletQueryService {

    private final WalletAccountService walletAccountService;

    public WalletQueryService(WalletAccountService walletAccountService) {
        this.walletAccountService = walletAccountService;
    }

    public WalletSummaryResponse summary(int userId) {
        return new WalletSummaryResponse(
                userId,
                walletAccountService.balanceOfUser(userId),
                walletAccountService.statusOfUser(userId)
        );
    }
}
