package com.nowcoder.community.market.mapper;

import com.nowcoder.community.market.entity.MarketInventoryUnit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Mapper
public interface MarketInventoryUnitMapper {

    int insert(MarketInventoryUnit unit);

    int countAvailableByListingId(@Param("listingId") long listingId);

    List<MarketInventoryUnit> selectAvailableForUpdate(@Param("listingId") long listingId,
                                                       @Param("limit") int limit);

    List<MarketInventoryUnit> selectByReservedOrderId(@Param("reservedOrderId") long reservedOrderId);

    List<MarketInventoryUnit> selectByListingId(@Param("listingId") long listingId);

    MarketInventoryUnit selectById(@Param("inventoryUnitId") long inventoryUnitId);

    int invalidateAvailable(@Param("inventoryUnitId") long inventoryUnitId,
                            @Param("sellerUserId") int sellerUserId);

    int reserveForOrder(@Param("inventoryUnitId") long inventoryUnitId,
                        @Param("reservedOrderId") long reservedOrderId);

    int markDeliveredByOrder(@Param("reservedOrderId") long reservedOrderId,
                             @Param("status") String status,
                             @Param("deliveredAt") java.util.Date deliveredAt);

    int releaseReservedByOrder(@Param("reservedOrderId") long reservedOrderId);
}
