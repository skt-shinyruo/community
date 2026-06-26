package com.nowcoder.community.growth.domain.repository;

import java.util.List;
import java.util.UUID;

public interface UserTaskEventLogRepository {

    record UserTaskContributionLog(UUID userId, String taskCode, String periodKey, String sourceEventId) {
    }

    int insert(UUID id, UUID userId, String taskCode, String periodKey, String sourceEventId);

    List<UserTaskContributionLog> findLikeContributionLogs(UUID userId, String relationKey);

    int deleteByUserTaskPeriodAndSourceEventId(UUID userId, String taskCode, String periodKey, String sourceEventId);
}
