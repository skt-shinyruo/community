package com.nowcoder.community.growth.application;

import com.nowcoder.community.growth.domain.model.TaskTemplate;
import com.nowcoder.community.growth.domain.model.UserTaskProgress;
import com.nowcoder.community.growth.domain.repository.TaskTemplateRepository;
import com.nowcoder.community.growth.domain.repository.UserTaskEventLogRepository;
import com.nowcoder.community.growth.domain.repository.UserTaskProgressRepository;
import com.nowcoder.community.wallet.api.action.WalletRewardActionApi;
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
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskProgressApplicationServiceUnitTest {

    @Mock
    private TaskTemplateRepository taskTemplateRepository;

    @Mock
    private UserTaskProgressRepository userTaskProgressRepository;

    @Mock
    private UserTaskEventLogRepository userTaskEventLogRepository;

    @Mock
    private WalletRewardActionApi walletRewardService;

    @Test
    void concurrentProgressInitShouldRecoverFromDuplicateRowAndContinueWithLockedState() {
        GrowthBusinessTimeService businessTimeService = new GrowthBusinessTimeService(
                Clock.fixed(Instant.parse("2026-03-22T00:00:00Z"), ZoneId.of("Asia/Shanghai")),
                ZoneId.of("Asia/Shanghai")
        );
        TaskProgressApplicationService service = new TaskProgressApplicationService(
                taskTemplateRepository,
                userTaskProgressRepository,
                userTaskEventLogRepository,
                walletRewardService,
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
        locked.setId(UUID.fromString("00000000-0000-7000-8000-000000000631"));
        locked.setTaskCode("DAILY_POST");
        locked.setPeriodKey("2026-03-22");
        locked.setCurrentValue(0);
        locked.setTargetValue(1);
        locked.setStatus("IN_PROGRESS");
        UUID userId = uuid(1);

        when(taskTemplateRepository.findActiveByTriggerEventType("PostPublished")).thenReturn(List.of(template));
        when(userTaskEventLogRepository.insert(any(UUID.class), eq(userId), eq("DAILY_POST"), eq("2026-03-22"), eq("post-evt-1"))).thenReturn(1);
        when(userTaskProgressRepository.insert(any(UUID.class), eq(userId), eq("DAILY_POST"), eq("2026-03-22"), eq(1), eq("IN_PROGRESS"), isNull()))
                .thenThrow(new DataIntegrityViolationException("duplicate progress"));
        when(userTaskProgressRepository.findByUserTaskAndPeriodForUpdate(userId, "DAILY_POST", "2026-03-22")).thenReturn(locked);

        service.processEvent(userId, "PostPublished", "post-evt-1", LocalDate.of(2026, 3, 22));

        verify(walletRewardService).issue("task:" + userId + ":DAILY_POST:2026-03-22", userId, 1L, "DAILY_POST");
        verify(userTaskProgressRepository).updateProgress(any(UUID.class), anyInt(), anyString(), any(), any(), anyString(), anyString());
    }

    @Test
    void duplicateProcessedEventShouldReturnWithoutTouchingProgress() {
        GrowthBusinessTimeService businessTimeService = new GrowthBusinessTimeService(
                Clock.fixed(Instant.parse("2026-03-22T00:00:00Z"), ZoneId.of("Asia/Shanghai")),
                ZoneId.of("Asia/Shanghai")
        );
        TaskProgressApplicationService service = new TaskProgressApplicationService(
                taskTemplateRepository,
                userTaskProgressRepository,
                userTaskEventLogRepository,
                walletRewardService,
                businessTimeService
        );

        TaskTemplate template = new TaskTemplate();
        template.setTaskCode("DAILY_POST");
        template.setTaskType("CONTENT");
        template.setPeriodType("DAILY");
        template.setTriggerEventType("PostPublished");
        template.setTargetValue(1);
        UUID userId = uuid(1);

        when(taskTemplateRepository.findActiveByTriggerEventType("PostPublished")).thenReturn(List.of(template));
        when(userTaskEventLogRepository.insert(any(UUID.class), eq(userId), eq("DAILY_POST"), eq("2026-03-22"), eq("post-evt-1")))
                .thenThrow(new DataIntegrityViolationException("duplicate task event"));

        service.processEvent(userId, "PostPublished", "post-evt-1", LocalDate.of(2026, 3, 22));

        verify(userTaskProgressRepository, never()).findByUserTaskAndPeriodForUpdate(any(UUID.class), anyString(), anyString());
        verify(userTaskProgressRepository, never()).updateProgress(any(UUID.class), anyInt(), anyString(), any(), any(), anyString(), anyString());
        verify(walletRewardService, never()).issue(anyString(), any(UUID.class), anyLong(), anyString());
    }
}
