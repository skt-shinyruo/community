package com.nowcoder.community.wallet.mapper;

import com.nowcoder.community.wallet.entity.RechargeOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@Mapper
public interface RechargeOrderMapper {

    RechargeOrder selectByRequestId(@Param("requestId") String requestId);

    RechargeOrder selectByUserIdAndRequestId(@Param("userId") UUID userId,
                                             @Param("requestId") String requestId);

    int insert(RechargeOrder order);

    int updateStatus(@Param("userId") UUID userId,
                     @Param("requestId") String requestId,
                     @Param("fromStatus") String fromStatus,
                     @Param("toStatus") String toStatus);
}
