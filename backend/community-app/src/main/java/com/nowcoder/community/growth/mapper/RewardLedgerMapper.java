package com.nowcoder.community.growth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Mapper
public interface RewardLedgerMapper {

    int insert(
            @Param("userId") int userId,
            @Param("eventId") String eventId,
            @Param("eventType") String eventType,
            @Param("delta") int delta,
            @Param("balanceAfter") int balanceAfter,
            @Param("frozenBalanceAfter") int frozenBalanceAfter,
            @Param("sourceModule") String sourceModule,
            @Param("remark") String remark
    );

    List<com.nowcoder.community.growth.entity.RewardLedgerEntry> selectRecentByUserId(@Param("userId") int userId, @Param("limit") int limit);
}
