package com.nowcoder.community.wallet.mapper;

import com.nowcoder.community.wallet.entity.WalletAdminAction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Mapper
public interface WalletAdminActionMapper {

    int insert(WalletAdminAction action);

    WalletAdminAction selectByRequestId(@Param("requestId") String requestId);

    List<WalletAdminAction> selectRecentByTargetAccountId(@Param("targetAccountId") long targetAccountId,
                                                          @Param("limit") int limit);
}
