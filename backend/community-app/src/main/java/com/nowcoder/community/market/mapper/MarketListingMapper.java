package com.nowcoder.community.market.mapper;

import com.nowcoder.community.market.entity.MarketListing;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Mapper
public interface MarketListingMapper {

    int insert(MarketListing listing);

    MarketListing selectById(@Param("listingId") long listingId);

    MarketListing selectByIdForUpdate(@Param("listingId") long listingId);

    List<MarketListing> selectBySellerUserId(@Param("sellerUserId") int sellerUserId);

    List<MarketListing> selectPublicListings();

    int updateEditable(MarketListing listing);

    int updateStatus(@Param("listingId") long listingId,
                     @Param("sellerUserId") int sellerUserId,
                     @Param("status") String status);

    int adjustStock(@Param("listingId") long listingId,
                    @Param("sellerUserId") int sellerUserId,
                    @Param("deltaTotal") int deltaTotal,
                    @Param("deltaAvailable") int deltaAvailable,
                    @Param("nextStatus") String nextStatus);
}
