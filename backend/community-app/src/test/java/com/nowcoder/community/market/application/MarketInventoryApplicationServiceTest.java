package com.nowcoder.community.market.application;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.market.domain.repository.MarketInventoryRepository;
import com.nowcoder.community.market.domain.repository.MarketListingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class MarketInventoryApplicationServiceTest {

    @Test
    void appendInventoryShouldRejectNullCommand() {
        MarketInventoryApplicationService service = new MarketInventoryApplicationService(
                mock(MarketListingRepository.class),
                mock(MarketInventoryRepository.class),
                new UuidV7Generator()
        );

        assertThatThrownBy(() -> service.appendInventory(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }
}
