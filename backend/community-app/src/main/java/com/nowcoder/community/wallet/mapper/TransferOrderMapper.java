package com.nowcoder.community.wallet.mapper;

import com.nowcoder.community.wallet.entity.TransferOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@Mapper
public interface TransferOrderMapper {

    TransferOrder selectByRequestId(@Param("requestId") String requestId);

    TransferOrder selectByFromUserIdAndRequestId(@Param("fromUserId") UUID fromUserId,
                                                @Param("requestId") String requestId);

    int insert(TransferOrder order);

    int updateStatus(@Param("fromUserId") UUID fromUserId,
                     @Param("requestId") String requestId,
                     @Param("fromStatus") String fromStatus,
                     @Param("toStatus") String toStatus);
}
