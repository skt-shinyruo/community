package com.nowcoder.community.wallet.infrastructure.persistence.mapper;

import com.nowcoder.community.wallet.infrastructure.persistence.dataobject.TransferOrderDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@Mapper
public interface TransferOrderMapper {

    TransferOrderDataObject selectByRequestId(@Param("requestId") String requestId);

    TransferOrderDataObject selectByFromUserIdAndRequestId(@Param("fromUserId") UUID fromUserId,
                                                @Param("requestId") String requestId);

    int insert(TransferOrderDataObject order);

    int updateStatus(@Param("fromUserId") UUID fromUserId,
                     @Param("requestId") String requestId,
                     @Param("fromStatus") String fromStatus,
                     @Param("toStatus") String toStatus);
}
