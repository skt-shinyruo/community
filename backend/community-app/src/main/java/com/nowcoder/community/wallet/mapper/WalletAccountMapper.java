package com.nowcoder.community.wallet.mapper;

import com.nowcoder.community.wallet.entity.WalletAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@Mapper
public interface WalletAccountMapper {

    WalletAccount selectByAccountId(@Param("accountId") UUID accountId);

    WalletAccount selectByOwner(@Param("ownerType") String ownerType,
                                @Param("ownerId") UUID ownerId,
                                @Param("accountType") String accountType);

    int insert(WalletAccount account);

    int updateBalanceWithVersion(@Param("accountId") UUID accountId,
                                 @Param("expectedVersion") long expectedVersion,
                                 @Param("delta") long delta,
                                 @Param("nextStatus") String nextStatus);
}
