package com.nowcoder.community.wallet.application;

import com.nowcoder.community.wallet.application.WalletRewardApplicationService.RewardCommand;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class WalletRewardProjectionApplicationServiceTest {

    @Test
    void shouldBuildStableWalletCommandFromBusinessSource() {
        WalletRewardApplicationService walletRewardApplicationService = mock(WalletRewardApplicationService.class);
        WalletRewardProjectionApplicationService service =
                new WalletRewardProjectionApplicationService(walletRewardApplicationService);
        UUID userId = uuid(7);

        service.postPublished(uuid(100), userId);

        verify(walletRewardApplicationService).applyDelta(new RewardCommand(
                "wallet-reward:post-published:" + uuid(100), userId, 10, "PostPublished"
        ));
    }

    @Test
    void selfLikeShouldNotCreateWalletDelta() {
        WalletRewardApplicationService walletRewardApplicationService = mock(WalletRewardApplicationService.class);
        WalletRewardProjectionApplicationService service =
                new WalletRewardProjectionApplicationService(walletRewardApplicationService);
        UUID userId = uuid(7);

        service.likeCreated("like:source", userId, userId);

        verifyNoInteractions(walletRewardApplicationService);
    }

    @Test
    void lifecycleActionsShouldProduceDistinctIdempotentWalletRequests() {
        WalletRewardApplicationService walletRewardApplicationService = mock(WalletRewardApplicationService.class);
        WalletRewardProjectionApplicationService service =
                new WalletRewardProjectionApplicationService(walletRewardApplicationService);
        UUID ownerUserId = uuid(8);
        String lifecycleSource = uuid(801).toString();

        service.likeRemoved(lifecycleSource + ":removed", uuid(7), ownerUserId);
        service.likeCreated(lifecycleSource + ":created", uuid(7), ownerUserId);
        service.likeCreated(lifecycleSource + ":created", uuid(7), ownerUserId);

        verify(walletRewardApplicationService).applyDelta(new RewardCommand(
                "wallet-reward:" + lifecycleSource + ":removed", ownerUserId, -1, "LikeRemoved"
        ));
        verify(walletRewardApplicationService, times(2)).applyDelta(new RewardCommand(
                "wallet-reward:" + lifecycleSource + ":created", ownerUserId, 1, "LikeCreated"
        ));
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
