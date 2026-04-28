package com.nowcoder.community.market.infrastructure.persistence.mapper;

import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketInventoryUnitDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@Mapper
public interface MarketInventoryUnitMapper {

    int insert(MarketInventoryUnitDataObject unit);

    int countAvailableByListingId(@Param("listingId") UUID listingId);

    List<MarketInventoryUnitDataObject> selectAvailableForUpdate(@Param("listingId") UUID listingId,
                                                                 @Param("limit") int limit);

    List<MarketInventoryUnitDataObject> selectByReservedOrderId(@Param("reservedOrderId") UUID reservedOrderId);

    List<MarketInventoryUnitDataObject> selectByListingId(@Param("listingId") UUID listingId);

    MarketInventoryUnitDataObject selectById(@Param("inventoryUnitId") UUID inventoryUnitId);

    int invalidateAvailable(@Param("inventoryUnitId") UUID inventoryUnitId,
                            @Param("sellerUserId") UUID sellerUserId);

    int reserveForOrder(@Param("inventoryUnitId") UUID inventoryUnitId,
                        @Param("reservedOrderId") UUID reservedOrderId);

    int markDeliveredByOrder(@Param("reservedOrderId") UUID reservedOrderId,
                             @Param("status") String status,
                             @Param("deliveredAt") java.util.Date deliveredAt);

    int markDeliveredByOrderIfReserved(@Param("orderId") UUID orderId,
                                       @Param("deliveredAt") java.util.Date deliveredAt);

    int releaseReservedByOrder(@Param("reservedOrderId") UUID reservedOrderId);

    int releaseReservedByOrderIfNeeded(@Param("orderId") UUID orderId);
}
