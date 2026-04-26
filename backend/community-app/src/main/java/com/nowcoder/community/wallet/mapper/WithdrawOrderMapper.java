package com.nowcoder.community.wallet.mapper;

import com.nowcoder.community.wallet.entity.WithdrawOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@Mapper
public interface WithdrawOrderMapper {

    WithdrawOrder selectByRequestId(@Param("requestId") String requestId);

    WithdrawOrder selectByUserIdAndRequestId(@Param("userId") UUID userId,
                                            @Param("requestId") String requestId);

    int insert(WithdrawOrder order);

    int updateStatus(@Param("userId") UUID userId,
                     @Param("requestId") String requestId,
                     @Param("fromStatus") String fromStatus,
                     @Param("toStatus") String toStatus);
}
