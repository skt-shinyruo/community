package com.nowcoder.community.growth.infrastructure.persistence.mapper;

import com.nowcoder.community.growth.domain.repository.UserTaskEventLogRepository.UserTaskContributionLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@Mapper
public interface UserTaskEventLogMapper {

    int insert(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("taskCode") String taskCode,
            @Param("periodKey") String periodKey,
            @Param("sourceEventId") String sourceEventId
    );

    boolean exists(
            @Param("userId") UUID userId,
            @Param("taskCode") String taskCode,
            @Param("periodKey") String periodKey,
            @Param("sourceEventId") String sourceEventId
    );

    List<UserTaskContributionLog> selectLikeContributionLogsForUpdate(
            @Param("userId") UUID userId,
            @Param("contributionSourceId") String contributionSourceId
    );

    int countByUserTaskAndPeriod(
            @Param("userId") UUID userId,
            @Param("taskCode") String taskCode,
            @Param("periodKey") String periodKey
    );

    int deleteByUserTaskPeriodAndSourceEventId(
            @Param("userId") UUID userId,
            @Param("taskCode") String taskCode,
            @Param("periodKey") String periodKey,
            @Param("sourceEventId") String sourceEventId
    );
}
