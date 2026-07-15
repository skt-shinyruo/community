package com.nowcoder.community.wallet.application;

import com.nowcoder.community.wallet.application.command.RewardProjectionCommand;
import com.nowcoder.community.wallet.application.command.WalletRewardCommand;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WalletRewardProjectionApplicationServiceTest {

    @Test
    void shouldBuildStableWalletCommandFromBusinessSource() {
        WalletRewardApplicationService walletRewardApplicationService = mock(WalletRewardApplicationService.class);
        WalletRewardProjectionApplicationService service =
                new WalletRewardProjectionApplicationService(walletRewardApplicationService);
        UUID userId = uuid(7);
        RewardProjectionCommand command = service.commandForPostPublished(uuid(100), userId);

        service.apply(command);

        verify(walletRewardApplicationService).applyDelta(new WalletRewardCommand(
                "wallet-reward:post-published:" + uuid(100), userId, 10, "PostPublished"
        ));
    }

    @Test
    void selfLikeShouldNotCreateProjectionCommand() {
        WalletRewardProjectionApplicationService service =
                new WalletRewardProjectionApplicationService(mock(WalletRewardApplicationService.class));
        UUID userId = uuid(7);

        assertThat(service.commandForLikeCreated("like:source", userId, userId)).isNull();
    }

    @Test
    void applyShouldRejectNullCommand() {
        WalletRewardProjectionApplicationService service =
                new WalletRewardProjectionApplicationService(mock(WalletRewardApplicationService.class));

        assertThatThrownBy(() -> service.apply(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
