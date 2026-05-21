package com.nowcoder.community.wallet.infrastructure.api;

import com.nowcoder.community.wallet.api.query.WalletAccountQueryApi;
import com.nowcoder.community.wallet.application.WalletAccountApplicationService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class WalletAccountQueryApiAdapter implements WalletAccountQueryApi {

    private final WalletAccountApplicationService walletAccountApplicationService;

    public WalletAccountQueryApiAdapter(WalletAccountApplicationService walletAccountApplicationService) {
        this.walletAccountApplicationService = walletAccountApplicationService;
    }

    @Override
    public long balanceOfUser(UUID userId) {
        return walletAccountApplicationService.balanceOfUser(userId);
    }

    @Override
    public String statusOfUser(UUID userId) {
        return walletAccountApplicationService.statusOfUser(userId);
    }
}
