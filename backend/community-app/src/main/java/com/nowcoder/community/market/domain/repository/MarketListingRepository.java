package com.nowcoder.community.market.domain.repository;

import com.nowcoder.community.market.domain.model.MarketListing;

import java.util.List;
import java.util.UUID;

public interface MarketListingRepository {

    enum StatusTransitionResult {
        APPLIED,
        STALE
    }

    int save(MarketListing listing);

    MarketListing findById(UUID listingId);

    MarketListing lockById(UUID listingId);

    List<MarketListing> findBySellerUserId(UUID sellerUserId, long offset, int limit);

    List<MarketListing> findPublicListings(long offset, int limit);

    int saveEditable(MarketListing listing);

    StatusTransitionResult transitionStatus(
            UUID listingId,
            UUID sellerUserId,
            String expectedStatus,
            String nextStatus
    );

    int adjustStock(
            UUID listingId,
            UUID sellerUserId,
            int deltaTotal,
            int deltaAvailable,
            String expectedStatus,
            String nextStatus
    );
}
