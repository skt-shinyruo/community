package com.nowcoder.community.wallet.mapper;

import com.nowcoder.community.wallet.entity.WalletAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Repository
@Mapper
public interface WalletAccountMapper {

    WalletAccount selectByAccountId(@Param("accountId") long accountId);

    WalletAccount selectByOwner(@Param("ownerType") String ownerType,
                                @Param("ownerId") long ownerId,
                                @Param("accountType") String accountType);

    int insert(WalletAccount account);

    int updateBalanceWithVersion(@Param("accountId") long accountId,
                                 @Param("expectedVersion") long expectedVersion,
                                 @Param("delta") long delta,
                                 @Param("nextStatus") String nextStatus);
}
