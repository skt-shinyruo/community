package com.nowcoder.community.wallet.infrastructure.persistence.mapper;

import com.nowcoder.community.wallet.infrastructure.persistence.dataobject.WalletAdminActionDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@Mapper
public interface WalletAdminActionMapper {

    int insert(WalletAdminActionDataObject action);

    WalletAdminActionDataObject selectByRequestId(@Param("requestId") String requestId);

    List<WalletAdminActionDataObject> selectRecentByTargetAccountId(@Param("targetAccountId") UUID targetAccountId,
                                                          @Param("limit") int limit);
}
