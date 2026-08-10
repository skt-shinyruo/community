package com.nowcoder.community.growth.infrastructure.persistence.mapper;

import com.nowcoder.community.growth.domain.model.TaskTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Mapper
public interface TaskTemplateMapper {

    List<TaskTemplate> selectActiveByTriggerEventType(String triggerEventType);

    List<TaskTemplate> selectActiveOrdered();
}
