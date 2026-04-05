package com.nowcoder.community.market.mapper;

import com.nowcoder.community.market.entity.MarketDispute;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Mapper
public interface MarketDisputeMapper {

    int insert(MarketDispute dispute);

    MarketDispute selectById(@Param("disputeId") long disputeId);

    List<MarketDispute> selectByOrderId(@Param("orderId") long orderId);

    List<MarketDispute> selectOpenDisputes();

    int update(MarketDispute dispute);
}
