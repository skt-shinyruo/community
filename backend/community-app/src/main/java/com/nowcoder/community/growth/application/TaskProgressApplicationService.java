package com.nowcoder.community.growth.application;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.growth.application.command.RecordTaskProgressCommand;
import com.nowcoder.community.growth.application.command.TriggerCommentCreatedCommand;
import com.nowcoder.community.growth.application.command.TriggerLikeCreatedCommand;
import com.nowcoder.community.growth.application.command.TriggerLikeRemovedCommand;
import com.nowcoder.community.growth.application.command.TriggerPostPublishedCommand;
import com.nowcoder.community.growth.domain.model.TaskTemplate;
import com.nowcoder.community.growth.domain.model.UserTaskProgress;
import com.nowcoder.community.growth.domain.repository.TaskTemplateRepository;
import com.nowcoder.community.growth.domain.repository.UserTaskEventLogRepository;
import com.nowcoder.community.growth.domain.repository.UserTaskProgressRepository;
import com.nowcoder.community.growth.domain.service.RewardGrantDomainService;
import com.nowcoder.community.growth.domain.service.TaskProgressDomainService;
import com.nowcoder.community.wallet.api.action.WalletRewardActionApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class TaskProgressApplicationService {

    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_CLAIMABLE = "CLAIMABLE";
    private static final String STATUS_CLAIMED = "CLAIMED";
    private static final String TRIGGER_POST_PUBLISHED = "PostPublished";
    private static final String TRIGGER_COMMENT_CREATED = "CommentCreated";
    private static final String TRIGGER_LIKE_CREATED = "LikeCreated";
    private final TaskTemplateRepository taskTemplateRepository;
    private final UserTaskProgressRepository userTaskProgressRepository;
    private final UserTaskEventLogRepository userTaskEventLogRepository;
    private final WalletRewardActionApi walletRewardActionApi;
    private final GrowthBusinessTimeService growthBusinessTimeService;
    private final TaskProgressDomainService taskProgressDomainService;
    private final RewardGrantDomainService rewardGrantDomainService;
    private final UuidV7Generator idGenerator;

    @Autowired
    public TaskProgressApplicationService(
            TaskTemplateRepository taskTemplateRepository,
            UserTaskProgressRepository userTaskProgressRepository,
            UserTaskEventLogRepository userTaskEventLogRepository,
            WalletRewardActionApi walletRewardActionApi,
            GrowthBusinessTimeService growthBusinessTimeService
    ) {
        this(
                taskTemplateRepository,
                userTaskProgressRepository,
                userTaskEventLogRepository,
                walletRewardActionApi,
                growthBusinessTimeService,
                new TaskProgressDomainService(),
                new RewardGrantDomainService(),
                new UuidV7Generator()
        );
    }

    TaskProgressApplicationService(
            TaskTemplateRepository taskTemplateRepository,
            UserTaskProgressRepository userTaskProgressRepository,
            UserTaskEventLogRepository userTaskEventLogRepository,
            WalletRewardActionApi walletRewardActionApi,
            GrowthBusinessTimeService growthBusinessTimeService,
            TaskProgressDomainService taskProgressDomainService,
            RewardGrantDomainService rewardGrantDomainService,
            UuidV7Generator idGenerator
    ) {
        this.taskTemplateRepository = taskTemplateRepository;
        this.userTaskProgressRepository = userTaskProgressRepository;
        this.userTaskEventLogRepository = userTaskEventLogRepository;
        this.walletRewardActionApi = walletRewardActionApi;
        this.growthBusinessTimeService = growthBusinessTimeService;
        this.taskProgressDomainService = taskProgressDomainService;
        this.rewardGrantDomainService = rewardGrantDomainService;
        this.idGenerator = idGenerator;
    }

    private void recordProgress(RecordTaskProgressCommand command) {
        if (command == null || !taskProgressDomainService.isProcessableEvent(command.userId(), command.triggerEventType(), command.sourceEventId(), command.bizDate())) {
            return;
        }
        String triggerEventType = command.triggerEventType().trim();
        String sourceEventId = command.sourceEventId().trim();
        List<TaskTemplate> templates = taskTemplateRepository.findActiveByTriggerEventType(triggerEventType);
        for (TaskTemplate template : templates) {
            if (template == null || template.getTargetValue() <= 0) {
                continue;
            }
            applyTemplate(command.userId(), template, sourceEventId, command.bizDate());
        }
    }

    @Transactional
    public void processEvent(UUID userId, String triggerEventType, String sourceEventId, LocalDate bizDate) {
        recordProgress(new RecordTaskProgressCommand(userId, triggerEventType, sourceEventId, bizDate));
    }

    @Transactional
    public void triggerPostPublished(TriggerPostPublishedCommand command) {
        if (command == null || command.postId() == null || command.userId() == null || command.createTime() == null) {
            return;
        }
        process(command.userId(), TRIGGER_POST_PUBLISHED, "post-published:" + command.postId(), command.createTime());
    }

    @Transactional
    public void triggerCommentCreated(TriggerCommentCreatedCommand command) {
        if (command == null || command.commentId() == null || command.userId() == null || command.createTime() == null) {
            return;
        }
        process(command.userId(), TRIGGER_COMMENT_CREATED, "comment-created:" + command.commentId(), command.createTime());
    }

    @Transactional
    public void triggerLikeCreated(TriggerLikeCreatedCommand command) {
        if (command == null || !StringUtils.hasText(command.sourceEventId()) || command.createTime() == null) {
            return;
        }
        UUID toUserId = command.entityUserId();
        if (toUserId == null || toUserId.equals(command.actorUserId())) {
            return;
        }
        process(toUserId, TRIGGER_LIKE_CREATED, command.sourceEventId().trim(), command.createTime());
    }

    @Transactional
    public void triggerLikeRemoved(TriggerLikeRemovedCommand command) {
        if (command == null || !StringUtils.hasText(command.relationKey()) || command.entityUserId() == null) {
            return;
        }
        rollbackLikeCreatedProgress(command.entityUserId(), command.relationKey().trim());
    }

    private void process(UUID userId, String triggerEventType, String sourceEventId, Instant occurredAt) {
        if (userId == null || !StringUtils.hasText(triggerEventType) || !StringUtils.hasText(sourceEventId) || occurredAt == null) {
            return;
        }
        LocalDate bizDate = growthBusinessTimeService.dateOf(occurredAt);
        if (bizDate == null) {
            return;
        }
        recordProgress(new RecordTaskProgressCommand(userId, triggerEventType, sourceEventId, bizDate));
    }

    private void applyTemplate(UUID userId, TaskTemplate template, String sourceEventId, LocalDate bizDate) {
        String periodKey = taskProgressDomainService.periodKey(template.getPeriodType(), bizDate);
        if (!recordSourceEvent(userId, template.getTaskCode(), periodKey, sourceEventId)) {
            return;
        }
        ensureProgressRowExists(userId, template, periodKey);
        UserTaskProgress progress = userTaskProgressRepository.findByUserTaskAndPeriodForUpdate(userId, template.getTaskCode(), periodKey);
        if (progress == null) {
            throw new IllegalStateException("task progress init failed: taskCode=" + template.getTaskCode());
        }
        if (STATUS_CLAIMED.equals(progress.getStatus()) && progress.getRewardGrantId() != null) {
            return;
        }

        int nextValue = taskProgressDomainService.cappedDelta(progress.getCurrentValue(), template.getTargetValue(), 1);
        Date now = growthBusinessTimeService.startOfDayDate(bizDate);
        boolean reached = nextValue >= template.getTargetValue();
        Date reachedAt = progress.getReachedAt();
        String rewardGrantId = progress.getRewardGrantId();
        Date claimedAt = progress.getClaimedAt();
        String nextStatus = STATUS_IN_PROGRESS;

        if (reached && reachedAt == null) {
            reachedAt = now;
        }

        if (reached) {
            if (template.isClaimRequired()) {
                nextStatus = STATUS_CLAIMABLE;
            } else if (rewardGrantId == null) {
                rewardGrantId = rewardGrantDomainService.taskRewardGrantId(userId, template.getTaskCode(), periodKey);
                long rewardAmount = rewardGrantDomainService.rewardAmount(template.getRewardGrowthDelta(), template.getRewardBalanceDelta());
                if (rewardAmount > 0) {
                    walletRewardActionApi.issue(rewardGrantId, userId, rewardAmount, template.getTaskCode());
                }
                nextStatus = STATUS_CLAIMED;
                claimedAt = now;
            } else {
                nextStatus = STATUS_CLAIMED;
            }
        }

        userTaskProgressRepository.updateProgress(
                progress.getId(),
                nextValue,
                nextStatus,
                reachedAt,
                claimedAt,
                rewardGrantId,
                sourceEventId
        );
    }

    private boolean recordSourceEvent(UUID userId, String taskCode, String periodKey, String sourceEventId) {
        if (!rewardGrantDomainService.hasValidSourceEventId(sourceEventId)) {
            return false;
        }
        try {
            userTaskEventLogRepository.insert(idGenerator.next(), userId, taskCode, periodKey, sourceEventId);
            return true;
        } catch (DataIntegrityViolationException ignored) {
            return false;
        }
    }

    private void ensureProgressRowExists(UUID userId, TaskTemplate template, String periodKey) {
        try {
            userTaskProgressRepository.insert(idGenerator.next(), userId, template.getTaskCode(), periodKey, template.getTargetValue(), STATUS_IN_PROGRESS, null);
        } catch (DataIntegrityViolationException ignored) {
            // Another event created the row first; lock and continue.
        }
    }

    private void rollbackLikeCreatedProgress(UUID userId, String relationKey) {
        List<UserTaskEventLogRepository.UserTaskContributionLog> logs =
                userTaskEventLogRepository.findLikeContributionLogs(userId, relationKey);
        if (logs == null || logs.isEmpty()) {
            return;
        }
        for (UserTaskEventLogRepository.UserTaskContributionLog log : logs) {
            rollbackLikeContribution(log, relationKey);
        }
    }

    private void rollbackLikeContribution(UserTaskEventLogRepository.UserTaskContributionLog log, String relationKey) {
        if (log == null) {
            return;
        }
        UserTaskProgress progress = userTaskProgressRepository.findByUserTaskAndPeriodForUpdate(
                log.userId(),
                log.taskCode(),
                log.periodKey()
        );
        if (progress == null) {
            userTaskEventLogRepository.deleteByUserTaskPeriodAndSourceEventId(
                    log.userId(),
                    log.taskCode(),
                    log.periodKey(),
                    log.sourceEventId()
            );
            return;
        }
        if (STATUS_CLAIMED.equals(progress.getStatus())) {
            return;
        }

        int nextValue = Math.max(progress.getCurrentValue() - 1, 0);
        boolean stillReached = nextValue >= progress.getTargetValue();
        userTaskProgressRepository.updateProgress(
                progress.getId(),
                nextValue,
                stillReached ? STATUS_CLAIMABLE : STATUS_IN_PROGRESS,
                stillReached ? progress.getReachedAt() : null,
                null,
                null,
                relationKey
        );
        userTaskEventLogRepository.deleteByUserTaskPeriodAndSourceEventId(
                log.userId(),
                log.taskCode(),
                log.periodKey(),
                log.sourceEventId()
        );
    }
}
