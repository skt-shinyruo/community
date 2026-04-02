package com.nowcoder.community.wallet.mapper;

import com.nowcoder.community.wallet.entity.WalletEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Mapper
public interface WalletEntryMapper {

    int insert(WalletEntry entry);

    List<WalletEntry> selectByTxnId(@Param("txnId") long txnId);
}
