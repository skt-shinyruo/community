package com.nowcoder.community.market.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.market.application.command.AddMarketInventoryBatchCommand;
import com.nowcoder.community.market.domain.model.MarketListing;
import com.nowcoder.community.market.domain.repository.MarketInventoryRepository;
import com.nowcoder.community.market.domain.repository.MarketListingRepository;
import com.nowcoder.community.market.exception.MarketErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void appendInventoryShouldCarryExpectedStatusAndMapStaleWriteToConflict() {
        UUID listingId = UUID.fromString("00000000-0000-7000-8000-000000000007");
        UUID sellerUserId = UUID.fromString("00000000-0000-7000-8000-000000000008");
        MarketListingRepository listingRepository = mock(MarketListingRepository.class);
        MarketInventoryRepository inventoryRepository = mock(MarketInventoryRepository.class);
        MarketListing listing = new MarketListing();
        listing.setListingId(listingId);
        listing.setSellerUserId(sellerUserId);
        listing.setGoodsType("VIRTUAL");
        listing.setDeliveryMode("PRELOADED");
        listing.setStockMode("FINITE");
        listing.setStockAvailable(1);
        listing.setStatus("PAUSED");
        when(listingRepository.lockById(listingId)).thenReturn(listing);
        when(listingRepository.adjustStock(listingId, sellerUserId, 1, 1, "PAUSED", "PAUSED"))
                .thenReturn(0);
        MarketInventoryApplicationService service = new MarketInventoryApplicationService(
                listingRepository,
                inventoryRepository,
                new UuidV7Generator()
        );

        assertThatThrownBy(() -> service.appendInventory(new AddMarketInventoryBatchCommand(
                listingId,
                sellerUserId,
                "TEXT",
                List.of("replacement-code")
                )))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(MarketErrorCode.LISTING_TRANSITION_CONFLICT));
        verify(listingRepository).adjustStock(listingId, sellerUserId, 1, 1, "PAUSED", "PAUSED");
    }
}
