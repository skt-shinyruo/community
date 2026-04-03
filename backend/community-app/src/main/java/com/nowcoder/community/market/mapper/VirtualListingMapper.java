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

    List<VirtualListing> selectPublicListings();

    int updateEditable(VirtualListing listing);

    int updateStatus(@Param("listingId") long listingId,
                     @Param("sellerUserId") int sellerUserId,
                     @Param("status") String status);

    int adjustStock(@Param("listingId") long listingId,
                    @Param("sellerUserId") int sellerUserId,
                    @Param("deltaTotal") int deltaTotal,
                    @Param("deltaAvailable") int deltaAvailable,
                    @Param("nextStatus") String nextStatus);
}
