package com.nowcoder.community.growth.infrastructure.persistence.mapper;

import com.nowcoder.community.growth.infrastructure.persistence.dataobject.UserTaskProgressDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.UUID;

@Repository
@Mapper
public interface UserTaskProgressMapper {

    UserTaskProgressDataObject selectByUserTaskAndPeriod(@Param("userId") UUID userId, @Param("taskCode") String taskCode, @Param("periodKey") String periodKey);

    UserTaskProgressDataObject selectByUserTaskAndPeriodForUpdate(@Param("userId") UUID userId, @Param("taskCode") String taskCode, @Param("periodKey") String periodKey);

    int countCompletedByUserTaskAndPeriodKeyRange(
            @Param("userId") UUID userId,
            @Param("taskCode") String taskCode,
            @Param("startPeriodKey") String startPeriodKey,
            @Param("endPeriodKey") String endPeriodKey
    );

    int insert(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("taskCode") String taskCode,
            @Param("periodKey") String periodKey,
            @Param("targetValue") int targetValue,
            @Param("status") String status,
            @Param("lastSourceEventId") String lastSourceEventId
    );

    int updateProgress(
            @Param("id") UUID id,
            @Param("currentValue") int currentValue,
            @Param("status") String status,
            @Param("reachedAt") Date reachedAt,
            @Param("claimedAt") Date claimedAt,
            @Param("rewardGrantId") String rewardGrantId,
            @Param("lastSourceEventId") String lastSourceEventId
    );
}
