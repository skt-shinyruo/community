package com.nowcoder.community.growth.infrastructure.persistence;

import com.nowcoder.community.growth.domain.repository.UserTaskEventLogRepository;
import com.nowcoder.community.growth.infrastructure.persistence.mapper.UserTaskEventLogMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class MyBatisUserTaskEventLogRepository implements UserTaskEventLogRepository {

    private final UserTaskEventLogMapper userTaskEventLogMapper;

    public MyBatisUserTaskEventLogRepository(UserTaskEventLogMapper userTaskEventLogMapper) {
        this.userTaskEventLogMapper = userTaskEventLogMapper;
    }

    @Override
    public CreateStatus create(UUID id, UUID userId, String taskCode, String periodKey, String sourceEventId) {
        try {
            return userTaskEventLogMapper.insert(id, userId, taskCode, periodKey, sourceEventId) == 1
                    ? CreateStatus.CREATED
                    : CreateStatus.CONFLICT;
        } catch (DuplicateKeyException ignored) {
            return userTaskEventLogMapper.exists(userId, taskCode, periodKey, sourceEventId)
                    ? CreateStatus.ALREADY_EXISTS
                    : CreateStatus.CONFLICT;
        }
    }

    @Override
    public boolean exists(UUID userId, String taskCode, String periodKey, String sourceEventId) {
        return userTaskEventLogMapper.exists(userId, taskCode, periodKey, sourceEventId);
    }

    @Override
    public List<UserTaskContributionLog> findLikeContributionLogsForUpdate(UUID userId, String contributionSourceId) {
        return userTaskEventLogMapper.selectLikeContributionLogsForUpdate(userId, contributionSourceId);
    }

    @Override
    public int countByUserTaskAndPeriod(UUID userId, String taskCode, String periodKey) {
        return userTaskEventLogMapper.countByUserTaskAndPeriod(userId, taskCode, periodKey);
    }

    @Override
    public int deleteByUserTaskPeriodAndSourceEventId(UUID userId, String taskCode, String periodKey, String sourceEventId) {
        return userTaskEventLogMapper.deleteByUserTaskPeriodAndSourceEventId(userId, taskCode, periodKey, sourceEventId);
    }
}
