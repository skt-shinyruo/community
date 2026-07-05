package com.nowcoder.community.wallet.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class WalletRewardApplicationServiceTest {

    @Test
    void issueShouldRejectNullCommand() {
        WalletRewardApplicationService service = new WalletRewardApplicationService(
                mock(WalletAccountApplicationService.class),
                mock(WalletLedgerApplicationService.class)
        );

        assertThatThrownBy(() -> service.issue(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void revokeShouldRejectNullCommand() {
        WalletRewardApplicationService service = new WalletRewardApplicationService(
                mock(WalletAccountApplicationService.class),
                mock(WalletLedgerApplicationService.class)
        );

        assertThatThrownBy(() -> service.revoke(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void applyDeltaShouldRejectNullCommand() {
        WalletRewardApplicationService service = new WalletRewardApplicationService(
                mock(WalletAccountApplicationService.class),
                mock(WalletLedgerApplicationService.class)
        );

        assertThatThrownBy(() -> service.applyDelta(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }
}
