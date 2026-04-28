package com.nowcoder.community.growth.infrastructure.persistence;

import com.nowcoder.community.growth.domain.repository.UserTaskEventLogRepository;
import com.nowcoder.community.growth.infrastructure.persistence.mapper.UserTaskEventLogMapper;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class MyBatisUserTaskEventLogRepository implements UserTaskEventLogRepository {

    private final UserTaskEventLogMapper userTaskEventLogMapper;

    public MyBatisUserTaskEventLogRepository(UserTaskEventLogMapper userTaskEventLogMapper) {
        this.userTaskEventLogMapper = userTaskEventLogMapper;
    }

    @Override
    public int insert(UUID id, UUID userId, String taskCode, String periodKey, String sourceEventId) {
        return userTaskEventLogMapper.insert(id, userId, taskCode, periodKey, sourceEventId);
    }
}
