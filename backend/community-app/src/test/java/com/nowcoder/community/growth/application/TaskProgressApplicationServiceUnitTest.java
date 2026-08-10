package com.nowcoder.community.growth.application;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.growth.application.TaskProgressApplicationService.TriggerLikeCreatedCommand;
import com.nowcoder.community.growth.application.TaskProgressApplicationService.TriggerLikeRemovedCommand;
import com.nowcoder.community.growth.domain.model.TaskTemplate;
import com.nowcoder.community.growth.domain.model.UserTaskProgress;
import com.nowcoder.community.growth.domain.repository.LikeTaskLifecycleStateRepository;
import com.nowcoder.community.growth.domain.repository.TaskTemplateRepository;
import com.nowcoder.community.growth.domain.repository.UserTaskEventLogRepository;
import com.nowcoder.community.growth.domain.repository.UserTaskProgressRepository;
import com.nowcoder.community.wallet.api.action.WalletRewardActionApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
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
    private LikeTaskLifecycleStateRepository likeTaskLifecycleStateRepository;

    @Mock
    private WalletRewardActionApi walletRewardService;

    @Test
    void concurrentProgressInitShouldRecoverFromDuplicateRowAndContinueWithLockedState() {
        GrowthBusinessTimeService businessTimeService = new GrowthBusinessTimeService(
                "Asia/Shanghai",
                Clock.fixed(Instant.parse("2026-03-22T00:00:00Z"), ZoneId.of("Asia/Shanghai"))
        );
        TaskProgressApplicationService service = new TaskProgressApplicationService(
                taskTemplateRepository,
                userTaskProgressRepository,
                userTaskEventLogRepository,
                likeTaskLifecycleStateRepository,
                walletRewardService,
                businessTimeService,
                new UuidV7Generator()
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
        when(userTaskEventLogRepository.create(any(UUID.class), eq(userId), eq("DAILY_POST"), eq("2026-03-22"), eq("post-evt-1")))
                .thenReturn(UserTaskEventLogRepository.CreateStatus.CREATED);
        when(userTaskProgressRepository.create(any(UUID.class), eq(userId), eq("DAILY_POST"), eq("2026-03-22"), eq(1), eq("IN_PROGRESS"), isNull()))
                .thenReturn(new UserTaskProgressRepository.CreateResult(
                        UserTaskProgressRepository.CreateStatus.ALREADY_EXISTS,
                        locked
                ));
        when(userTaskProgressRepository.findByUserTaskAndPeriodForUpdate(userId, "DAILY_POST", "2026-03-22")).thenReturn(locked);

        service.processEvent(userId, "PostPublished", "post-evt-1", LocalDate.of(2026, 3, 22));

        verify(walletRewardService).issue("task:" + userId + ":DAILY_POST:2026-03-22", userId, 1L, "DAILY_POST");
        verify(userTaskProgressRepository).updateProgress(any(UUID.class), anyInt(), anyString(), any(), any(), anyString(), anyString());
    }

    @Test
    void duplicateProcessedEventShouldReturnWithoutTouchingProgress() {
        GrowthBusinessTimeService businessTimeService = new GrowthBusinessTimeService(
                "Asia/Shanghai",
                Clock.fixed(Instant.parse("2026-03-22T00:00:00Z"), ZoneId.of("Asia/Shanghai"))
        );
        TaskProgressApplicationService service = new TaskProgressApplicationService(
                taskTemplateRepository,
                userTaskProgressRepository,
                userTaskEventLogRepository,
                likeTaskLifecycleStateRepository,
                walletRewardService,
                businessTimeService,
                new UuidV7Generator()
        );

        TaskTemplate template = new TaskTemplate();
        template.setTaskCode("DAILY_POST");
        template.setTaskType("CONTENT");
        template.setPeriodType("DAILY");
        template.setTriggerEventType("PostPublished");
        template.setTargetValue(1);
        UUID userId = uuid(1);

        when(taskTemplateRepository.findActiveByTriggerEventType("PostPublished")).thenReturn(List.of(template));
        when(userTaskEventLogRepository.create(any(UUID.class), eq(userId), eq("DAILY_POST"), eq("2026-03-22"), eq("post-evt-1")))
                .thenReturn(UserTaskEventLogRepository.CreateStatus.ALREADY_EXISTS);

        service.processEvent(userId, "PostPublished", "post-evt-1", LocalDate.of(2026, 3, 22));

        verify(userTaskProgressRepository, never()).findByUserTaskAndPeriodForUpdate(any(UUID.class), anyString(), anyString());
        verify(userTaskProgressRepository, never()).updateProgress(any(UUID.class), anyInt(), anyString(), any(), any(), anyString(), anyString());
        verify(walletRewardService, never()).issue(anyString(), any(UUID.class), anyLong(), anyString());
    }

    @Test
    void likeLifecycleTransactionsShouldUseCurrentReadsForEveryProgressRecalculation() throws Exception {
        Transactional createTransaction = TaskProgressApplicationService.class
                .getMethod("triggerLikeCreated", TriggerLikeCreatedCommand.class)
                .getAnnotation(Transactional.class);
        Transactional removeTransaction = TaskProgressApplicationService.class
                .getMethod("triggerLikeRemoved", TriggerLikeRemovedCommand.class)
                .getAnnotation(Transactional.class);

        assertThat(createTransaction).isNotNull();
        assertThat(removeTransaction).isNotNull();
        assertThat(createTransaction.isolation()).isEqualTo(Isolation.READ_COMMITTED);
        assertThat(removeTransaction.isolation()).isEqualTo(Isolation.READ_COMMITTED);
    }
}
