package com.nowcoder.community.growth.domain.repository;

import com.nowcoder.community.growth.domain.model.UserTaskProgress;

import java.util.Date;
import java.util.UUID;

public interface UserTaskProgressRepository {

    UserTaskProgress findByUserTaskAndPeriod(UUID userId, String taskCode, String periodKey);

    UserTaskProgress findByUserTaskAndPeriodForUpdate(UUID userId, String taskCode, String periodKey);

    int countByUserTaskAndPeriodKeyRange(UUID userId, String taskCode, String startPeriodKey, String endPeriodKey);

    int insert(UUID id, UUID userId, String taskCode, String periodKey, int targetValue, String status, String lastSourceEventId);

    int updateProgress(UUID id, int currentValue, String status, Date reachedAt, Date claimedAt, String rewardGrantId, String lastSourceEventId);
}
