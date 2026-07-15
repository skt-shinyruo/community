package com.nowcoder.community.growth.infrastructure.persistence;

import com.nowcoder.community.growth.domain.model.UserTaskProgress;
import com.nowcoder.community.growth.domain.repository.UserTaskProgressRepository;
import com.nowcoder.community.growth.infrastructure.persistence.mapper.UserTaskProgressMapper;
import org.springframework.dao.DuplicateKeyException;
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
    public CreateResult create(UUID id, UUID userId, String taskCode, String periodKey, int targetValue, String status, String lastSourceEventId) {
        try {
            return userTaskProgressMapper.insert(id, userId, taskCode, periodKey, targetValue, status, lastSourceEventId) == 1
                    ? new CreateResult(CreateStatus.CREATED, null)
                    : new CreateResult(CreateStatus.CONFLICT, null);
        } catch (DuplicateKeyException ignored) {
            UserTaskProgress existing = findByUserTaskAndPeriod(userId, taskCode, periodKey);
            if (matchesCreateTuple(existing, userId, taskCode, periodKey, targetValue)) {
                return new CreateResult(CreateStatus.ALREADY_EXISTS, existing);
            }
            return new CreateResult(CreateStatus.CONFLICT, existing);
        }
    }

    private static boolean matchesCreateTuple(
            UserTaskProgress existing,
            UUID userId,
            String taskCode,
            String periodKey,
            int targetValue
    ) {
        return existing != null
                && userId.equals(existing.getUserId())
                && taskCode.equals(existing.getTaskCode())
                && periodKey.equals(existing.getPeriodKey())
                && targetValue == existing.getTargetValue();
    }

    @Override
    public int updateProgress(UUID id, int currentValue, String status, Date reachedAt, Date claimedAt, String rewardGrantId, String lastSourceEventId) {
        return userTaskProgressMapper.updateProgress(id, currentValue, status, reachedAt, claimedAt, rewardGrantId, lastSourceEventId);
    }
}
