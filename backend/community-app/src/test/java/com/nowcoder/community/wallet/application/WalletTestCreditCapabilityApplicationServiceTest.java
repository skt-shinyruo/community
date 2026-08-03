package com.nowcoder.community.wallet.application;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WalletTestCreditCapabilityApplicationServiceTest {

    @Test
    void discardRemainingShouldBeBoundedByTheUsersUndiscardedGrants() {
        WalletTestCreditProperties properties = new WalletTestCreditProperties();
        properties.setEnabled(true);
        properties.setGrantEnabled(true);
        properties.setDiscardEnabled(true);
        properties.setGrantQuotaPerUser(5_000L);
        properties.setDiscardQuotaPerUser(5_000L);
        WalletTestCreditQuotaPort quotaPort = mock(WalletTestCreditQuotaPort.class);
        UUID userId = uuid(101);
        when(quotaPort.usage(userId)).thenReturn(new WalletTestCreditQuotaPort.Usage(600L, 250L));

        var result = new WalletTestCreditCapabilityApplicationService(
                new WalletTestCreditPolicy(properties),
                quotaPort
        ).capabilities(userId);

        assertThat(result.testCredits().grant().remainingAmount()).isEqualTo(4_400L);
        assertThat(result.testCredits().discard().remainingAmount()).isEqualTo(350L);
    }
}
