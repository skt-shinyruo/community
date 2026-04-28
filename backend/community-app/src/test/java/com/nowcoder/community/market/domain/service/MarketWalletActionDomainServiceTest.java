package com.nowcoder.community.market.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketWalletActionDomainServiceTest {

    private final MarketWalletActionDomainService service = new MarketWalletActionDomainService();

    @Test
    void requestIdShouldUseOrderAndLowercaseActionType() {
        assertThat(service.requestId("ESCROW", uuid(1)))
                .isEqualTo("market-order:" + uuid(1) + ":escrow");
    }

    @Test
    void terminalStatusShouldRejectDifferentNextStatus() {
        assertThatThrownBy(() -> service.validateTerminalTransition("SUCCEEDED", "FAILED"))
                .isInstanceOf(BusinessException.class);
    }
}
