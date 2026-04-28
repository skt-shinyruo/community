package com.nowcoder.community.market.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketListingDomainServiceTest {

    private final MarketListingDomainService service = new MarketListingDomainService();

    @Test
    void listingShouldRejectNonPositivePriceAndStock() {
        assertThatThrownBy(() -> service.validateCreateListing(uuid(1), "name", 0, 1))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.validateCreateListing(uuid(1), "name", 100, 0))
                .isInstanceOf(BusinessException.class);
    }
}
