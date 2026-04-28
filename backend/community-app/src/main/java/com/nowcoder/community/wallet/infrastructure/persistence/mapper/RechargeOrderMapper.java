package com.nowcoder.community.wallet.infrastructure.persistence.mapper;

import com.nowcoder.community.wallet.infrastructure.persistence.dataobject.RechargeOrderDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@Mapper
public interface RechargeOrderMapper {

    RechargeOrderDataObject selectByRequestId(@Param("requestId") String requestId);

    RechargeOrderDataObject selectByUserIdAndRequestId(@Param("userId") UUID userId,
                                             @Param("requestId") String requestId);

    int insert(RechargeOrderDataObject order);

    int updateStatus(@Param("userId") UUID userId,
                     @Param("requestId") String requestId,
                     @Param("fromStatus") String fromStatus,
                     @Param("toStatus") String toStatus);
}
