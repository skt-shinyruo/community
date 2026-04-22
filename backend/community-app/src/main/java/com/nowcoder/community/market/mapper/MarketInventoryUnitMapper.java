package com.nowcoder.community.market.mapper;

import com.nowcoder.community.market.entity.MarketInventoryUnit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@Mapper
public interface MarketInventoryUnitMapper {

    int insert(MarketInventoryUnit unit);

    int countAvailableByListingId(@Param("listingId") UUID listingId);

    List<MarketInventoryUnit> selectAvailableForUpdate(@Param("listingId") UUID listingId,
                                                       @Param("limit") int limit);

    List<MarketInventoryUnit> selectByReservedOrderId(@Param("reservedOrderId") UUID reservedOrderId);

    List<MarketInventoryUnit> selectByListingId(@Param("listingId") UUID listingId);

    MarketInventoryUnit selectById(@Param("inventoryUnitId") UUID inventoryUnitId);

    int invalidateAvailable(@Param("inventoryUnitId") UUID inventoryUnitId,
                            @Param("sellerUserId") UUID sellerUserId);

    int reserveForOrder(@Param("inventoryUnitId") UUID inventoryUnitId,
                        @Param("reservedOrderId") UUID reservedOrderId);

    int markDeliveredByOrder(@Param("reservedOrderId") UUID reservedOrderId,
                             @Param("status") String status,
                             @Param("deliveredAt") java.util.Date deliveredAt);

    int releaseReservedByOrder(@Param("reservedOrderId") UUID reservedOrderId);
}
