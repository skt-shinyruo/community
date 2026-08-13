package com.nowcoder.community.market.infrastructure.persistence.mapper;

import com.nowcoder.community.market.domain.model.MarketListing;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@Mapper
public interface MarketListingMapper {

    int insert(MarketListing listing);

    MarketListing selectById(@Param("listingId") UUID listingId);

    MarketListing selectByIdForUpdate(@Param("listingId") UUID listingId);

    List<MarketListing> selectBySellerUserId(@Param("sellerUserId") UUID sellerUserId,
                                             @Param("offset") long offset,
                                             @Param("limit") int limit);

    List<MarketListing> selectPublicListings(@Param("offset") long offset,
                                             @Param("limit") int limit);

    int updateEditable(MarketListing listing);

    int updateStatus(@Param("listingId") UUID listingId,
                     @Param("sellerUserId") UUID sellerUserId,
                     @Param("expectedStatus") String expectedStatus,
                     @Param("nextStatus") String nextStatus);

    int adjustStock(@Param("listingId") UUID listingId,
                    @Param("sellerUserId") UUID sellerUserId,
                    @Param("deltaTotal") int deltaTotal,
                    @Param("deltaAvailable") int deltaAvailable,
                    @Param("expectedStatus") String expectedStatus,
                    @Param("nextStatus") String nextStatus);
}
