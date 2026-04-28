package com.nowcoder.community.market.infrastructure.persistence.mapper;

import com.nowcoder.community.market.infrastructure.persistence.dataobject.MarketDisputeDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@Mapper
public interface MarketDisputeMapper {

    int insert(MarketDisputeDataObject dispute);

    MarketDisputeDataObject selectById(@Param("disputeId") UUID disputeId);

    List<MarketDisputeDataObject> selectByOrderId(@Param("orderId") UUID orderId);

    List<MarketDisputeDataObject> selectOpenDisputes();

    int update(MarketDisputeDataObject dispute);
}
