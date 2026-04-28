package com.nowcoder.community.market.infrastructure.persistence;

import com.nowcoder.community.market.domain.model.MarketListing;
import com.nowcoder.community.market.domain.repository.MarketListingRepository;
import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketListingDataObject;
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
    public int insert(MarketListing listing) {
        return mapper.insert(MarketListingDataObject.from(listing));
    }

    @Override
    public MarketListing selectById(UUID listingId) {
        return mapper.selectById(listingId);
    }

    @Override
    public MarketListing selectByIdForUpdate(UUID listingId) {
        return mapper.selectByIdForUpdate(listingId);
    }

    @Override
    public List<MarketListing> selectBySellerUserId(UUID sellerUserId) {
        return DomainRowAdapter.asDomainList(mapper.selectBySellerUserId(sellerUserId));
    }

    @Override
    public List<MarketListing> selectPublicListings() {
        return DomainRowAdapter.asDomainList(mapper.selectPublicListings());
    }

    @Override
    public int updateEditable(MarketListing listing) {
        return mapper.updateEditable(MarketListingDataObject.from(listing));
    }

    @Override
    public int updateStatus(UUID listingId, UUID sellerUserId, String status) {
        return mapper.updateStatus(listingId, sellerUserId, status);
    }

    @Override
    public int adjustStock(UUID listingId, UUID sellerUserId, int deltaTotal, int deltaAvailable, String nextStatus) {
        return mapper.adjustStock(listingId, sellerUserId, deltaTotal, deltaAvailable, nextStatus);
    }
}
