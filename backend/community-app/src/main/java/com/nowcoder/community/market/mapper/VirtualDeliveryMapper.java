package com.nowcoder.community.market.mapper;

import com.nowcoder.community.market.entity.VirtualDelivery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Mapper
public interface VirtualDeliveryMapper {

    int insert(VirtualDelivery delivery);

    List<VirtualDelivery> selectByOrderId(@Param("orderId") long orderId);
}
