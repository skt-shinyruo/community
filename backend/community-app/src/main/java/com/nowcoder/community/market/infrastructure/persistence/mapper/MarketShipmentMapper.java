package com.nowcoder.community.market.infrastructure.persistence.mapper;

import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketShipmentDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@Mapper
public interface MarketShipmentMapper {

    int insert(MarketShipmentDataObject shipment);

    MarketShipmentDataObject selectByOrderId(@Param("orderId") UUID orderId);
}
