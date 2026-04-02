package com.nowcoder.community.wallet.mapper;

import com.nowcoder.community.wallet.entity.RechargeOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Repository
@Mapper
public interface RechargeOrderMapper {

    RechargeOrder selectByRequestId(@Param("requestId") String requestId);

    int insert(RechargeOrder order);
}
