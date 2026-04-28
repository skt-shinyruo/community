package com.nowcoder.community.growth.application;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.growth.application.command.RecordTaskProgressCommand;
import com.nowcoder.community.growth.application.command.TriggerCommentCreatedCommand;
import com.nowcoder.community.growth.application.command.TriggerLikeCreatedCommand;
import com.nowcoder.community.growth.application.command.TriggerPostPublishedCommand;
import com.nowcoder.community.growth.domain.model.TaskTemplate;
import com.nowcoder.community.growth.domain.model.UserTaskProgress;
import com.nowcoder.community.growth.domain.repository.TaskTemplateRepository;
import com.nowcoder.community.growth.domain.repository.UserTaskEventLogRepository;
import com.nowcoder.community.growth.domain.repository.UserTaskProgressRepository;
import com.nowcoder.community.growth.domain.service.RewardGrantDomainService;
import com.nowcoder.community.growth.domain.service.TaskProgressDomainService;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
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

    @Transactional
    public void recordProgress(RecordTaskProgressCommand command) {
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

    public void processEvent(UUID userId, String triggerEventType, String sourceEventId, LocalDate bizDate) {
        recordProgress(new RecordTaskProgressCommand(userId, triggerEventType, sourceEventId, bizDate));
    }

    public void triggerPostPublished(TriggerPostPublishedCommand command) {
        if (command == null || command.postId() == null || command.userId() == null || command.createTime() == null) {
            return;
        }
        process(command.userId(), ContentEventTypes.POST_PUBLISHED, "post-published:" + command.postId(), command.createTime());
    }

    public void triggerCommentCreated(TriggerCommentCreatedCommand command) {
        if (command == null || command.commentId() == null || command.userId() == null || command.createTime() == null) {
            return;
        }
        process(command.userId(), ContentEventTypes.COMMENT_CREATED, "comment-created:" + command.commentId(), command.createTime());
    }

    public void triggerLikeCreated(TriggerLikeCreatedCommand command) {
        if (command == null || !StringUtils.hasText(command.sourceEventId()) || command.createTime() == null) {
            return;
        }
        UUID toUserId = command.entityUserId();
        if (toUserId == null || toUserId.equals(command.actorUserId())) {
            return;
        }
        process(toUserId, SocialEventTypes.LIKE_CREATED, command.sourceEventId().trim(), command.createTime());
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
}
