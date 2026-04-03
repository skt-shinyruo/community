package com.nowcoder.community.market.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.market.dto.VirtualListingDetailResponse;
import com.nowcoder.community.market.dto.VirtualListingResponse;
import com.nowcoder.community.market.entity.VirtualListing;
import com.nowcoder.community.market.mapper.VirtualListingMapper;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;

@Service
public class VirtualMarketQueryService {

    private final VirtualListingMapper virtualListingMapper;

    public VirtualMarketQueryService(VirtualListingMapper virtualListingMapper) {
        this.virtualListingMapper = virtualListingMapper;
    }

    public List<VirtualListingResponse> listPublicListings() {
        return virtualListingMapper.selectPublicListings().stream()
                .map(VirtualListingResponse::from)
                .toList();
    }

    public VirtualListingDetailResponse getListingDetail(long listingId) {
        VirtualListing listing = virtualListingMapper.selectById(listingId);
        if (listing == null) {
            throw new BusinessException(NOT_FOUND, "virtual listing not found: listingId=" + listingId);
        }
        return VirtualListingDetailResponse.from(listing);
    }
}
