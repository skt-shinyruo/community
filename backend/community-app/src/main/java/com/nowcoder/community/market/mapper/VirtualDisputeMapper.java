package com.nowcoder.community.market.mapper;

import com.nowcoder.community.market.entity.VirtualDispute;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Mapper
public interface VirtualDisputeMapper {

    int insert(VirtualDispute dispute);

    VirtualDispute selectById(@Param("disputeId") long disputeId);

    List<VirtualDispute> selectByOrderId(@Param("orderId") long orderId);

    List<VirtualDispute> selectOpenDisputes();

    int update(VirtualDispute dispute);
}
