package com.nowcoder.community.market.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketOrderDomainServiceTest {

    private final MarketOrderDomainService service = new MarketOrderDomainService();

    @Test
    void createOrderShouldRejectBuyingOwnListing() {
        UUID userId = uuid(1);

        assertThatThrownBy(() -> service.validateCreateOrder(userId, userId, 1))
                .isInstanceOf(BusinessException.class);
    }
}
