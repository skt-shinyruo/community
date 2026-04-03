package com.nowcoder.community.market.mapper;

import com.nowcoder.community.market.entity.VirtualInventoryUnit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Mapper
public interface VirtualInventoryUnitMapper {

    int insert(VirtualInventoryUnit unit);

    int countAvailableByListingId(@Param("listingId") long listingId);

    List<VirtualInventoryUnit> selectAvailableForUpdate(@Param("listingId") long listingId, @Param("limit") int limit);

    List<VirtualInventoryUnit> selectByReservedOrderId(@Param("reservedOrderId") long reservedOrderId);
}
