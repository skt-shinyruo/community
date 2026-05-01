package com.nowcoder.community.market.domain.repository;

import com.nowcoder.community.market.domain.model.MarketListing;

import java.util.List;
import java.util.UUID;

public interface MarketListingRepository {

    int save(MarketListing listing);

    MarketListing findById(UUID listingId);

    MarketListing lockById(UUID listingId);

    List<MarketListing> findBySellerUserId(UUID sellerUserId);

    List<MarketListing> findPublicListings();

    int saveEditable(MarketListing listing);

    int changeStatus(UUID listingId, UUID sellerUserId, String status);

    int adjustStock(UUID listingId, UUID sellerUserId, int deltaTotal, int deltaAvailable, String nextStatus);
}
