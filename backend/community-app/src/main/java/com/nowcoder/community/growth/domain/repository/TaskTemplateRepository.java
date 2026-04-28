package com.nowcoder.community.growth.domain.repository;

import com.nowcoder.community.growth.domain.model.TaskTemplate;

import java.util.List;

public interface TaskTemplateRepository {

    List<TaskTemplate> findActiveByTriggerEventType(String triggerEventType);

    List<TaskTemplate> findActiveOrdered();
}
