package com.nowcoder.community.growth.mapper;

import com.nowcoder.community.growth.entity.UserTaskProgress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;

@Repository
@Mapper
public interface UserTaskProgressMapper {

    UserTaskProgress selectByUserTaskAndPeriod(@Param("userId") int userId, @Param("taskCode") String taskCode, @Param("periodKey") String periodKey);

    UserTaskProgress selectByUserTaskAndPeriodForUpdate(@Param("userId") int userId, @Param("taskCode") String taskCode, @Param("periodKey") String periodKey);

    int insert(
            @Param("userId") int userId,
            @Param("taskCode") String taskCode,
            @Param("periodKey") String periodKey,
            @Param("targetValue") int targetValue,
            @Param("status") String status,
            @Param("lastSourceEventId") String lastSourceEventId
    );

    int updateProgress(
            @Param("id") long id,
            @Param("currentValue") int currentValue,
            @Param("status") String status,
            @Param("reachedAt") Date reachedAt,
            @Param("claimedAt") Date claimedAt,
            @Param("rewardGrantId") String rewardGrantId,
            @Param("lastSourceEventId") String lastSourceEventId
    );
}
