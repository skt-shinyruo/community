package com.nowcoder.community.growth.service;

import com.nowcoder.community.growth.dto.TaskCenterResponse;
import com.nowcoder.community.growth.dto.TaskItemResponse;
import com.nowcoder.community.growth.entity.TaskTemplate;
import com.nowcoder.community.growth.entity.UserTaskProgress;
import com.nowcoder.community.growth.mapper.TaskTemplateMapper;
import com.nowcoder.community.growth.mapper.UserTaskProgressMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class TaskCenterService {

    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";

    private final TaskTemplateMapper taskTemplateMapper;
    private final UserTaskProgressMapper userTaskProgressMapper;
    private final GrowthBusinessTimeService growthBusinessTimeService;

    public TaskCenterService(
            TaskTemplateMapper taskTemplateMapper,
            UserTaskProgressMapper userTaskProgressMapper,
            GrowthBusinessTimeService growthBusinessTimeService
    ) {
        this.taskTemplateMapper = taskTemplateMapper;
        this.userTaskProgressMapper = userTaskProgressMapper;
        this.growthBusinessTimeService = growthBusinessTimeService;
    }

    public TaskCenterResponse snapshot(int userId, LocalDate bizDate) {
        LocalDate safeBizDate = bizDate == null ? growthBusinessTimeService.today() : bizDate;
        TaskCenterResponse response = new TaskCenterResponse();
        response.setBizDate(safeBizDate);

        List<TaskItemResponse> items = new ArrayList<>();
        for (TaskTemplate template : taskTemplateMapper.selectActiveOrdered()) {
            items.add(toItem(userId, template, safeBizDate));
        }
        response.setItems(items);
        return response;
    }

    private TaskItemResponse toItem(int userId, TaskTemplate template, LocalDate bizDate) {
        String periodKey = TaskPeriodKeyResolver.resolve(template.getPeriodType(), bizDate);
        UserTaskProgress progress = userTaskProgressMapper.selectByUserTaskAndPeriod(userId, template.getTaskCode(), periodKey);

        TaskItemResponse item = new TaskItemResponse();
        item.setTaskCode(template.getTaskCode());
        item.setTaskType(template.getTaskType());
        item.setPeriodType(template.getPeriodType());
        item.setPeriodKey(periodKey);
        item.setCurrentValue(progress == null ? 0 : progress.getCurrentValue());
        item.setTargetValue(template.getTargetValue());
        item.setStatus(progress == null ? STATUS_IN_PROGRESS : progress.getStatus());
        item.setRewardGrowthDelta(template.getRewardGrowthDelta());
        item.setRewardBalanceDelta(template.getRewardBalanceDelta());
        item.setClaimRequired(template.isClaimRequired());
        item.setDisplayOrder(template.getDisplayOrder());
        return item;
    }
}
