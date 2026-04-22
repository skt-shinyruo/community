package com.nowcoder.community.growth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@Mapper
public interface RewardGrantRecordMapper {

    int insert(
            @Param("id") UUID id,
            @Param("grantId") String grantId,
            @Param("userId") UUID userId,
            @Param("grantType") String grantType,
            @Param("sourceEventId") String sourceEventId,
            @Param("sourceEventType") String sourceEventType,
            @Param("growthDelta") int growthDelta,
            @Param("rewardDelta") int rewardDelta,
            @Param("status") String status
    );
}
