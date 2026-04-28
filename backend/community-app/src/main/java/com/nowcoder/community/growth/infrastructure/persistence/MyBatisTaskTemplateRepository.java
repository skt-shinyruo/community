package com.nowcoder.community.growth.infrastructure.persistence;

import com.nowcoder.community.growth.domain.model.TaskTemplate;
import com.nowcoder.community.growth.domain.repository.TaskTemplateRepository;
import com.nowcoder.community.growth.infrastructure.persistence.mapper.TaskTemplateMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class MyBatisTaskTemplateRepository implements TaskTemplateRepository {

    private final TaskTemplateMapper taskTemplateMapper;

    public MyBatisTaskTemplateRepository(TaskTemplateMapper taskTemplateMapper) {
        this.taskTemplateMapper = taskTemplateMapper;
    }

    @Override
    public List<TaskTemplate> findActiveByTriggerEventType(String triggerEventType) {
        return new ArrayList<>(taskTemplateMapper.selectActiveByTriggerEventType(triggerEventType));
    }

    @Override
    public List<TaskTemplate> findActiveOrdered() {
        return new ArrayList<>(taskTemplateMapper.selectActiveOrdered());
    }
}
