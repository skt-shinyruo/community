package com.nowcoder.community.growth.infrastructure.persistence;

import com.nowcoder.community.growth.domain.model.UserTaskProgress;
import com.nowcoder.community.growth.domain.repository.UserTaskProgressRepository;
import com.nowcoder.community.growth.infrastructure.persistence.mapper.UserTaskProgressMapper;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.UUID;

@Repository
public class MyBatisUserTaskProgressRepository implements UserTaskProgressRepository {

    private final UserTaskProgressMapper userTaskProgressMapper;

    public MyBatisUserTaskProgressRepository(UserTaskProgressMapper userTaskProgressMapper) {
        this.userTaskProgressMapper = userTaskProgressMapper;
    }

    @Override
    public UserTaskProgress findByUserTaskAndPeriod(UUID userId, String taskCode, String periodKey) {
        return userTaskProgressMapper.selectByUserTaskAndPeriod(userId, taskCode, periodKey);
    }

    @Override
    public UserTaskProgress findByUserTaskAndPeriodForUpdate(UUID userId, String taskCode, String periodKey) {
        return userTaskProgressMapper.selectByUserTaskAndPeriodForUpdate(userId, taskCode, periodKey);
    }

    @Override
    public int countCompletedByUserTaskAndPeriodKeyRange(UUID userId, String taskCode, String startPeriodKey, String endPeriodKey) {
        return userTaskProgressMapper.countCompletedByUserTaskAndPeriodKeyRange(userId, taskCode, startPeriodKey, endPeriodKey);
    }

    @Override
    public int insert(UUID id, UUID userId, String taskCode, String periodKey, int targetValue, String status, String lastSourceEventId) {
        return userTaskProgressMapper.insert(id, userId, taskCode, periodKey, targetValue, status, lastSourceEventId);
    }

    @Override
    public int updateProgress(UUID id, int currentValue, String status, Date reachedAt, Date claimedAt, String rewardGrantId, String lastSourceEventId) {
        return userTaskProgressMapper.updateProgress(id, currentValue, status, reachedAt, claimedAt, rewardGrantId, lastSourceEventId);
    }
}
