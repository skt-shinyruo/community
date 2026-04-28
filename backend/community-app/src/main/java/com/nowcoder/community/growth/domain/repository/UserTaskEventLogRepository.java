package com.nowcoder.community.growth.domain.repository;

import java.util.UUID;

public interface UserTaskEventLogRepository {

    int insert(UUID id, UUID userId, String taskCode, String periodKey, String sourceEventId);
}
