package com.nowcoder.community.growth.infrastructure.persistence.mapper;

import com.nowcoder.community.growth.infrastructure.persistence.dataobject.TaskTemplateDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Mapper
public interface TaskTemplateMapper {

    List<TaskTemplateDataObject> selectActiveByTriggerEventType(String triggerEventType);

    List<TaskTemplateDataObject> selectActiveOrdered();
}
