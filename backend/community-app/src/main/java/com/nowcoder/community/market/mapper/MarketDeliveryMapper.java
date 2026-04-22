package com.nowcoder.community.market.mapper;

import com.nowcoder.community.market.entity.MarketDelivery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@Mapper
public interface MarketDeliveryMapper {

    int insert(MarketDelivery delivery);

    List<MarketDelivery> selectByOrderId(@Param("orderId") UUID orderId);
}
