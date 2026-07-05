package com.nowcoder.community.user.application;

import com.nowcoder.community.wallet.api.action.WalletRewardActionApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class UserRewardApplicationServiceTest {

    @Test
    void applyShouldRejectNullCommand() {
        UserRewardApplicationService service = new UserRewardApplicationService(mock(WalletRewardActionApi.class));

        assertThatThrownBy(() -> service.apply(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }
}
