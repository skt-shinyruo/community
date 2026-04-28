package com.nowcoder.community.market.domain.repository;

import com.nowcoder.community.market.domain.model.MarketListing;

import java.util.List;
import java.util.UUID;

public interface MarketListingRepository {

    int insert(MarketListing listing);

    MarketListing selectById(UUID listingId);

    MarketListing selectByIdForUpdate(UUID listingId);

    List<MarketListing> selectBySellerUserId(UUID sellerUserId);

    List<MarketListing> selectPublicListings();

    int updateEditable(MarketListing listing);

    int updateStatus(UUID listingId, UUID sellerUserId, String status);

    int adjustStock(UUID listingId, UUID sellerUserId, int deltaTotal, int deltaAvailable, String nextStatus);
}
