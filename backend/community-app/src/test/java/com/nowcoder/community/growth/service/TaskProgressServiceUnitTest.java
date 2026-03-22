package com.nowcoder.community.growth.service;

import com.nowcoder.community.growth.entity.TaskTemplate;
import com.nowcoder.community.growth.entity.UserTaskProgress;
import com.nowcoder.community.growth.mapper.TaskTemplateMapper;
import com.nowcoder.community.growth.mapper.UserTaskEventLogMapper;
import com.nowcoder.community.growth.mapper.UserTaskProgressMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskProgressServiceUnitTest {

    @Mock
    private TaskTemplateMapper taskTemplateMapper;

    @Mock
    private UserTaskProgressMapper userTaskProgressMapper;

    @Mock
    private UserTaskEventLogMapper userTaskEventLogMapper;

    @Mock
    private UnifiedGrantService unifiedGrantService;

    @Test
    void concurrentProgressInitShouldRecoverFromDuplicateRowAndContinueWithLockedState() {
        GrowthBusinessTimeService businessTimeService = new GrowthBusinessTimeService(
                Clock.fixed(Instant.parse("2026-03-22T00:00:00Z"), ZoneId.of("Asia/Shanghai")),
                ZoneId.of("Asia/Shanghai")
        );
        TaskProgressService service = new TaskProgressService(
                taskTemplateMapper,
                userTaskProgressMapper,
                userTaskEventLogMapper,
                unifiedGrantService,
                businessTimeService
        );

        TaskTemplate template = new TaskTemplate();
        template.setTaskCode("DAILY_POST");
        template.setTaskType("CONTENT");
        template.setPeriodType("DAILY");
        template.setTriggerEventType("PostPublished");
        template.setTargetValue(1);
        template.setRewardGrowthDelta(3);
        template.setRewardBalanceDelta(1);
        template.setClaimRequired(false);

        UserTaskProgress locked = new UserTaskProgress();
        locked.setId(11L);
        locked.setTaskCode("DAILY_POST");
        locked.setPeriodKey("2026-03-22");
        locked.setCurrentValue(0);
        locked.setTargetValue(1);
        locked.setStatus("IN_PROGRESS");

        when(taskTemplateMapper.selectActiveByTriggerEventType("PostPublished")).thenReturn(List.of(template));
        when(userTaskEventLogMapper.insert(1, "DAILY_POST", "2026-03-22", "post-evt-1")).thenReturn(1);
        when(userTaskProgressMapper.insert(1, "DAILY_POST", "2026-03-22", 1, "IN_PROGRESS", null))
                .thenThrow(new DataIntegrityViolationException("duplicate progress"));
        when(userTaskProgressMapper.selectByUserTaskAndPeriodForUpdate(1, "DAILY_POST", "2026-03-22")).thenReturn(locked);

        service.processEvent(1, "PostPublished", "post-evt-1", LocalDate.of(2026, 3, 22));

        verify(unifiedGrantService).applyGrant(
                1,
                "task:1:DAILY_POST:2026-03-22",
                "DAILY_POST",
                "post-evt-1",
                "PostPublished",
                3,
                1,
                "growth",
                "task-auto-grant"
        );
        verify(userTaskProgressMapper).updateProgress(anyLong(), anyInt(), anyString(), any(), any(), anyString(), anyString());
    }

    @Test
    void duplicateProcessedEventShouldReturnWithoutTouchingProgress() {
        GrowthBusinessTimeService businessTimeService = new GrowthBusinessTimeService(
                Clock.fixed(Instant.parse("2026-03-22T00:00:00Z"), ZoneId.of("Asia/Shanghai")),
                ZoneId.of("Asia/Shanghai")
        );
        TaskProgressService service = new TaskProgressService(
                taskTemplateMapper,
                userTaskProgressMapper,
                userTaskEventLogMapper,
                unifiedGrantService,
                businessTimeService
        );

        TaskTemplate template = new TaskTemplate();
        template.setTaskCode("DAILY_POST");
        template.setTaskType("CONTENT");
        template.setPeriodType("DAILY");
        template.setTriggerEventType("PostPublished");
        template.setTargetValue(1);

        when(taskTemplateMapper.selectActiveByTriggerEventType("PostPublished")).thenReturn(List.of(template));
        when(userTaskEventLogMapper.insert(1, "DAILY_POST", "2026-03-22", "post-evt-1"))
                .thenThrow(new DataIntegrityViolationException("duplicate task event"));

        service.processEvent(1, "PostPublished", "post-evt-1", LocalDate.of(2026, 3, 22));

        verify(userTaskProgressMapper, never()).selectByUserTaskAndPeriodForUpdate(anyInt(), anyString(), anyString());
        verify(userTaskProgressMapper, never()).updateProgress(anyLong(), anyInt(), anyString(), any(), any(), anyString(), anyString());
        verify(unifiedGrantService, never()).applyGrant(anyInt(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt(), anyString(), anyString());
    }
}
