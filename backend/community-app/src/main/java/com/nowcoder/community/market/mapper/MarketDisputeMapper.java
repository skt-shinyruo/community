package com.nowcoder.community.market.mapper;

import com.nowcoder.community.market.entity.MarketDispute;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@Mapper
public interface MarketDisputeMapper {

    int insert(MarketDispute dispute);

    MarketDispute selectById(@Param("disputeId") UUID disputeId);

    List<MarketDispute> selectByOrderId(@Param("orderId") UUID orderId);

    List<MarketDispute> selectOpenDisputes();

    int update(MarketDispute dispute);
}
