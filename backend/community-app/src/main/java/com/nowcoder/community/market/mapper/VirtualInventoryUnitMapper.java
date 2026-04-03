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

    List<VirtualInventoryUnit> selectByListingId(@Param("listingId") long listingId);

    VirtualInventoryUnit selectById(@Param("inventoryUnitId") long inventoryUnitId);

    int invalidateAvailable(@Param("inventoryUnitId") long inventoryUnitId, @Param("sellerUserId") int sellerUserId);

    int reserveForOrder(@Param("inventoryUnitId") long inventoryUnitId, @Param("reservedOrderId") long reservedOrderId);

    int markDeliveredByOrder(@Param("reservedOrderId") long reservedOrderId,
                             @Param("status") String status,
                             @Param("deliveredAt") java.util.Date deliveredAt);

    int releaseReservedByOrder(@Param("reservedOrderId") long reservedOrderId);
}
