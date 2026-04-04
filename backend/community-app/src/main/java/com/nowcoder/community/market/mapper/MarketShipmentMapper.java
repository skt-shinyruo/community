package com.nowcoder.community.market.mapper;

import com.nowcoder.community.market.entity.MarketShipment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Repository
@Mapper
public interface MarketShipmentMapper {

    int insert(MarketShipment shipment);

    MarketShipment selectByOrderId(@Param("orderId") long orderId);
}
