package com.nowcoder.community.market.infrastructure.persistence;

import com.nowcoder.community.market.domain.model.MarketListing;
import com.nowcoder.community.market.domain.repository.MarketListingRepository;
import com.nowcoder.community.market.infrastructure.persistence.mapper.MarketListingMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class MyBatisMarketListingRepository implements MarketListingRepository {

    private final MarketListingMapper mapper;

    public MyBatisMarketListingRepository(MarketListingMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int save(MarketListing listing) {
        return mapper.insert(listing);
    }

    @Override
    public MarketListing findById(UUID listingId) {
        return mapper.selectById(listingId);
    }

    @Override
    public MarketListing lockById(UUID listingId) {
        return mapper.selectByIdForUpdate(listingId);
    }

    @Override
    public List<MarketListing> findBySellerUserId(UUID sellerUserId, long offset, int limit) {
        return mapper.selectBySellerUserId(sellerUserId, offset, limit);
    }

    @Override
    public List<MarketListing> findPublicListings(long offset, int limit) {
        return mapper.selectPublicListings(offset, limit);
    }

    @Override
    public int saveEditable(MarketListing listing) {
        return mapper.updateEditable(listing);
    }

    @Override
    public StatusTransitionResult transitionStatus(
            UUID listingId,
            UUID sellerUserId,
            String expectedStatus,
            String nextStatus
    ) {
        return mapper.updateStatus(listingId, sellerUserId, expectedStatus, nextStatus) == 1
                ? StatusTransitionResult.APPLIED
                : StatusTransitionResult.STALE;
    }

    @Override
    public int adjustStock(
            UUID listingId,
            UUID sellerUserId,
            int deltaTotal,
            int deltaAvailable,
            String expectedStatus,
            String nextStatus
    ) {
        return mapper.adjustStock(
                listingId,
                sellerUserId,
                deltaTotal,
                deltaAvailable,
                expectedStatus,
                nextStatus
        );
    }
}
