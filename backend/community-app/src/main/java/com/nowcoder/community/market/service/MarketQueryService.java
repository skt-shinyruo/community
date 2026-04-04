package com.nowcoder.community.market.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.market.dto.MarketListingDetailResponse;
import com.nowcoder.community.market.dto.MarketListingResponse;
import com.nowcoder.community.market.entity.MarketListing;
import com.nowcoder.community.market.mapper.MarketListingMapper;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;

@Service
public class MarketQueryService {

    private final MarketListingMapper marketListingMapper;

    public MarketQueryService(MarketListingMapper marketListingMapper) {
        this.marketListingMapper = marketListingMapper;
    }

    public List<MarketListingResponse> listPublicListings() {
        return marketListingMapper.selectPublicListings().stream()
                .map(MarketListingResponse::from)
                .toList();
    }

    public MarketListingDetailResponse getListingDetail(long listingId) {
        MarketListing listing = marketListingMapper.selectById(listingId);
        if (listing == null) {
            throw new BusinessException(NOT_FOUND, "market listing not found: listingId=" + listingId);
        }
        return MarketListingDetailResponse.from(listing);
    }

    public List<MarketListingResponse> listSellerListings(int sellerUserId) {
        return marketListingMapper.selectBySellerUserId(sellerUserId).stream()
                .map(MarketListingResponse::from)
                .toList();
    }
}
