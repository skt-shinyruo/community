package com.nowcoder.community.wallet.infrastructure.api;

import com.nowcoder.community.wallet.api.action.WalletRewardActionApi;
import com.nowcoder.community.wallet.application.WalletRewardApplicationService;
import com.nowcoder.community.wallet.application.command.WalletRewardCommand;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class WalletRewardActionApiAdapter implements WalletRewardActionApi {

    private final WalletRewardApplicationService walletRewardApplicationService;

    public WalletRewardActionApiAdapter(WalletRewardApplicationService walletRewardApplicationService) {
        this.walletRewardApplicationService = walletRewardApplicationService;
    }

    @Override
    public void issue(String requestId, UUID userId, long amount, String sourceType) {
        walletRewardApplicationService.issue(new WalletRewardCommand(requestId, userId, amount, sourceType));
    }

    @Override
    public void applyDelta(String requestId, UUID userId, long amount, String sourceType) {
        walletRewardApplicationService.applyDelta(new WalletRewardCommand(requestId, userId, amount, sourceType));
    }
}
