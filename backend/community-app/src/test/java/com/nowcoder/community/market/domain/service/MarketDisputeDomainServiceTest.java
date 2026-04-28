package com.nowcoder.community.market.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketDisputeDomainServiceTest {

    private final MarketDisputeDomainService service = new MarketDisputeDomainService();

    @Test
    void disputeShouldAllowOnlyOrderParticipants() {
        assertThatThrownBy(() -> service.validateBuyerCanOpen(uuid(1), uuid(2)))
                .isInstanceOf(BusinessException.class);
    }
}
