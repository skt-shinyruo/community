package com.nowcoder.community.market.infrastructure.persistence.mapper;

import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketListingDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@Mapper
public interface MarketListingMapper {

    int insert(MarketListingDataObject listing);

    MarketListingDataObject selectById(@Param("listingId") UUID listingId);

    MarketListingDataObject selectByIdForUpdate(@Param("listingId") UUID listingId);

    List<MarketListingDataObject> selectBySellerUserId(@Param("sellerUserId") UUID sellerUserId);

    List<MarketListingDataObject> selectPublicListings();

    int updateEditable(MarketListingDataObject listing);

    int updateStatus(@Param("listingId") UUID listingId,
                     @Param("sellerUserId") UUID sellerUserId,
                     @Param("status") String status);

    int adjustStock(@Param("listingId") UUID listingId,
                    @Param("sellerUserId") UUID sellerUserId,
                    @Param("deltaTotal") int deltaTotal,
                    @Param("deltaAvailable") int deltaAvailable,
                    @Param("nextStatus") String nextStatus);
}
