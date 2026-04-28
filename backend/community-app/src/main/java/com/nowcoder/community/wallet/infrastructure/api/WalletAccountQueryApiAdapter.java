package com.nowcoder.community.wallet.infrastructure.api;

import com.nowcoder.community.wallet.api.query.WalletAccountQueryApi;
import com.nowcoder.community.wallet.application.WalletApplicationService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class WalletAccountQueryApiAdapter implements WalletAccountQueryApi {

    private final WalletApplicationService walletApplicationService;

    public WalletAccountQueryApiAdapter(WalletApplicationService walletApplicationService) {
        this.walletApplicationService = walletApplicationService;
    }

    @Override
    public long balanceOfUser(UUID userId) {
        return walletApplicationService.summary(userId).balance();
    }

    @Override
    public String statusOfUser(UUID userId) {
        return walletApplicationService.summary(userId).status();
    }
}
