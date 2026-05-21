package com.nowcoder.community.market.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarketListingTest {

    @Test
    void physicalListingShouldUseFiniteStockEvenWhenStockModeIsUnlimited() {
        MarketListing listing = listing("PHYSICAL", "MANUAL", "UNLIMITED", 5, "ACTIVE");

        assertThat(listing.goodsType()).isEqualTo(MarketGoodsType.PHYSICAL);
        assertThat(listing.deliveryMode()).isEqualTo(MarketDeliveryMode.MANUAL);
        assertThat(listing.stockMode()).isEqualTo(MarketStockMode.UNLIMITED);
        assertThat(listing.isActive()).isTrue();
        assertThat(listing.isFiniteStock()).isTrue();
    }

    @Test
    void virtualFiniteListingShouldUseFiniteStock() {
        MarketListing listing = listing("VIRTUAL", "PRELOADED", "FINITE", 2, "ACTIVE");

        assertThat(listing.isFiniteStock()).isTrue();
        assertThat(listing.isPreloadedDelivery()).isTrue();
    }

    @Test
    void stockDecreaseShouldReturnSoldOutWhenNextAvailableIsZero() {
        MarketListing listing = listing("VIRTUAL", "MANUAL", "FINITE", 1, "ACTIVE");

        assertThat(listing.statusAfterStockDecreasedBy(1)).isEqualTo("SOLD_OUT");
    }

    @Test
    void stockRestoreShouldReactivateSoldOutListingWhenAvailableBecomesPositive() {
        MarketListing listing = listing("VIRTUAL", "MANUAL", "FINITE", 0, "SOLD_OUT");

        assertThat(listing.statusAfterStockRestoredBy(1)).isEqualTo("ACTIVE");
    }

    private MarketListing listing(
            String goodsType,
            String deliveryMode,
            String stockMode,
            int stockAvailable,
            String status
    ) {
        MarketListing listing = new MarketListing();
        listing.setGoodsType(goodsType);
        listing.setDeliveryMode(deliveryMode);
        listing.setStockMode(stockMode);
        listing.setStockAvailable(stockAvailable);
        listing.setStatus(status);
        return listing;
    }
}
