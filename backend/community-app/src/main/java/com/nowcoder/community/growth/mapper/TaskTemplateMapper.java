package com.nowcoder.community.growth.mapper;

import com.nowcoder.community.growth.entity.TaskTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Mapper
public interface TaskTemplateMapper {

    List<TaskTemplate> selectActiveByTriggerEventType(String triggerEventType);

    List<TaskTemplate> selectActiveOrdered();
}
