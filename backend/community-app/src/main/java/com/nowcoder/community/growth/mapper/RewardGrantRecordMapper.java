package com.nowcoder.community.growth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Repository
@Mapper
public interface RewardGrantRecordMapper {

    int insert(
            @Param("grantId") String grantId,
            @Param("userId") int userId,
            @Param("grantType") String grantType,
            @Param("sourceEventId") String sourceEventId,
            @Param("sourceEventType") String sourceEventType,
            @Param("growthDelta") int growthDelta,
            @Param("rewardDelta") int rewardDelta,
            @Param("status") String status
    );
}
