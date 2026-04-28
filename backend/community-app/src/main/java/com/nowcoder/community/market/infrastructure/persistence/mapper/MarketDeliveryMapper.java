package com.nowcoder.community.market.infrastructure.persistence.mapper;

import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketDeliveryDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@Mapper
public interface MarketDeliveryMapper {

    int insert(MarketDeliveryDataObject delivery);

    List<MarketDeliveryDataObject> selectByOrderId(@Param("orderId") UUID orderId);
}
