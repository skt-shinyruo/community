package com.nowcoder.community.market.mapper;

import com.nowcoder.community.market.entity.VirtualListing;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Mapper
public interface VirtualListingMapper {

    int insert(VirtualListing listing);

    VirtualListing selectById(@Param("listingId") long listingId);

    VirtualListing selectByIdForUpdate(@Param("listingId") long listingId);

    List<VirtualListing> selectBySellerUserId(@Param("sellerUserId") int sellerUserId);
}
