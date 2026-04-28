package com.nowcoder.community.wallet.infrastructure.persistence.mapper;

import com.nowcoder.community.wallet.infrastructure.persistence.dataobject.WithdrawOrderDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@Mapper
public interface WithdrawOrderMapper {

    WithdrawOrderDataObject selectByRequestId(@Param("requestId") String requestId);

    WithdrawOrderDataObject selectByUserIdAndRequestId(@Param("userId") UUID userId,
                                            @Param("requestId") String requestId);

    int insert(WithdrawOrderDataObject order);

    int updateStatus(@Param("userId") UUID userId,
                     @Param("requestId") String requestId,
                     @Param("fromStatus") String fromStatus,
                     @Param("toStatus") String toStatus);
}
