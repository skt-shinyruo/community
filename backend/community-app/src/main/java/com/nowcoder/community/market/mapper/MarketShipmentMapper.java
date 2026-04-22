package com.nowcoder.community.market.mapper;

import com.nowcoder.community.market.entity.MarketShipment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@Mapper
public interface MarketShipmentMapper {

    int insert(MarketShipment shipment);

    MarketShipment selectByOrderId(@Param("orderId") UUID orderId);
}
