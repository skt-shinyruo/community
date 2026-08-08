package com.nowcoder.community.growth.application;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.growth.application.command.TriggerCommentCreatedCommand;
import com.nowcoder.community.growth.application.command.TriggerLikeCreatedCommand;
import com.nowcoder.community.growth.application.command.TriggerLikeRemovedCommand;
import com.nowcoder.community.growth.application.command.TriggerPostPublishedCommand;
import com.nowcoder.community.growth.domain.model.LikeTaskLifecycleState;
import com.nowcoder.community.growth.domain.model.TaskTemplate;
import com.nowcoder.community.growth.domain.model.UserTaskProgress;
import com.nowcoder.community.growth.domain.repository.LikeTaskLifecycleStateRepository;
import com.nowcoder.community.growth.domain.repository.TaskTemplateRepository;
import com.nowcoder.community.growth.domain.repository.UserTaskEventLogRepository;
import com.nowcoder.community.growth.domain.repository.UserTaskProgressRepository;
import com.nowcoder.community.growth.domain.service.RewardGrantDomainService;
import com.nowcoder.community.growth.domain.service.TaskProgressDomainService;
import com.nowcoder.community.wallet.api.action.WalletRewardActionApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
    private final LikeTaskLifecycleStateRepository likeTaskLifecycleStateRepository;
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
            LikeTaskLifecycleStateRepository likeTaskLifecycleStateRepository,
            WalletRewardActionApi walletRewardActionApi,
            GrowthBusinessTimeService growthBusinessTimeService
    ) {
        this(
                taskTemplateRepository,
                userTaskProgressRepository,
                userTaskEventLogRepository,
                likeTaskLifecycleStateRepository,
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
            LikeTaskLifecycleStateRepository likeTaskLifecycleStateRepository,
            WalletRewardActionApi walletRewardActionApi,
            GrowthBusinessTimeService growthBusinessTimeService,
            TaskProgressDomainService taskProgressDomainService,
            RewardGrantDomainService rewardGrantDomainService,
            UuidV7Generator idGenerator
    ) {
        this.taskTemplateRepository = taskTemplateRepository;
        this.userTaskProgressRepository = userTaskProgressRepository;
        this.userTaskEventLogRepository = userTaskEventLogRepository;
        this.likeTaskLifecycleStateRepository = likeTaskLifecycleStateRepository;
        this.walletRewardActionApi = walletRewardActionApi;
        this.growthBusinessTimeService = growthBusinessTimeService;
        this.taskProgressDomainService = taskProgressDomainService;
        this.rewardGrantDomainService = rewardGrantDomainService;
        this.idGenerator = idGenerator;
    }

    private void recordProgress(
            UUID userId,
            String triggerEventType,
            String sourceEventId,
            String dedupAlias,
            LocalDate bizDate
    ) {
        if (!taskProgressDomainService.isProcessableEvent(userId, triggerEventType, sourceEventId, bizDate)) {
            return;
        }
        String normalizedTriggerEventType = triggerEventType.trim();
        String normalizedSourceEventId = sourceEventId.trim();
        List<TaskTemplate> templates = new ArrayList<>(
                taskTemplateRepository.findActiveByTriggerEventType(normalizedTriggerEventType));
        templates.sort(Comparator.comparing(TaskTemplate::getTaskCode, Comparator.nullsLast(String::compareTo)));
        for (TaskTemplate template : templates) {
            if (template == null || template.getTargetValue() <= 0) {
                continue;
            }
            applyTemplate(userId, template, normalizedSourceEventId, dedupAlias, bizDate);
        }
    }

    @Transactional
    public void processEvent(UUID userId, String triggerEventType, String sourceEventId, LocalDate bizDate) {
        recordProgress(userId, triggerEventType, sourceEventId, null, bizDate);
    }

    @Transactional
    public void triggerPostPublished(TriggerPostPublishedCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (command.postId() == null || command.userId() == null || command.createTime() == null) {
            return;
        }
        process(command.userId(), TRIGGER_POST_PUBLISHED, "post-published:" + command.postId(), command.createTime());
    }

    @Transactional
    public void triggerCommentCreated(TriggerCommentCreatedCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (command.commentId() == null || command.userId() == null || command.createTime() == null) {
            return;
        }
        process(command.userId(), TRIGGER_COMMENT_CREATED, "comment-created:" + command.commentId(), command.createTime());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void triggerLikeCreated(TriggerLikeCreatedCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (!StringUtils.hasText(command.sourceEventId())
                || command.sourceVersion() <= 0L
                || !StringUtils.hasText(command.relationKey())
                || command.actorUserId() == null
                || command.createTime() == null) {
            return;
        }
        UUID toUserId = command.entityUserId();
        if (toUserId == null || toUserId.equals(command.actorUserId())) {
            return;
        }
        LikeTaskLifecycleState incoming = likeState(command, true);
        applyLikeLifecycle(likeTaskLifecycleStateRepository.advance(incoming), command.createTime());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void triggerLikeRemoved(TriggerLikeRemovedCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (!StringUtils.hasText(command.sourceEventId())
                || command.sourceVersion() <= 0L
                || !StringUtils.hasText(command.relationKey())
                || command.entityUserId() == null) {
            return;
        }
        LikeTaskLifecycleState incoming = likeState(command, false);
        applyLikeLifecycle(likeTaskLifecycleStateRepository.advance(incoming), null);
    }

    private void process(UUID userId, String triggerEventType, String sourceEventId, Instant occurredAt) {
        if (userId == null || !StringUtils.hasText(triggerEventType) || !StringUtils.hasText(sourceEventId) || occurredAt == null) {
            return;
        }
        LocalDate bizDate = growthBusinessTimeService.dateOf(occurredAt);
        if (bizDate == null) {
            return;
        }
        recordProgress(userId, triggerEventType, sourceEventId, null, bizDate);
    }

    private void processLikeActivation(LikeTaskLifecycleState state, Instant occurredAt) {
        if (state == null || occurredAt == null) {
            return;
        }
        LocalDate bizDate = growthBusinessTimeService.dateOf(occurredAt);
        if (bizDate == null) {
            return;
        }
        recordProgress(
                state.recipientUserId(),
                TRIGGER_LIKE_CREATED,
                contributionSourceId(state),
                state.relationKey(),
                bizDate
        );
    }

    private void applyLikeLifecycle(
            LikeTaskLifecycleStateRepository.AdvanceResult result,
            Instant activationOccurredAt
    ) {
        if (result == null || result.transition() == null) {
            throw new IllegalStateException("like task lifecycle advance returned no transition");
        }
        switch (result.transition()) {
            case ACTIVATED -> {
                if (result.previous() == null
                        && result.current() != null
                        && result.current().relationInstanceId() != null) {
                    rollbackLikeCreatedProgress(
                            result.current().recipientUserId(),
                            result.current().relationKey()
                    );
                }
                processLikeActivation(result.current(), activationOccurredAt);
            }
            case DEACTIVATED -> rollbackLikeState(
                    result.previous() != null && result.previous().active()
                            ? result.previous()
                            : result.current()
            );
            case REPLACED -> {
                rollbackLikeState(result.previous());
                processLikeActivation(result.current(), activationOccurredAt);
            }
            case ADVANCED, IGNORED -> {
                // Ordering state advances without changing the net task contribution.
            }
        }
    }

    private LikeTaskLifecycleState likeState(TriggerLikeCreatedCommand command, boolean active) {
        return new LikeTaskLifecycleState(
                command.entityUserId(),
                command.relationKey(),
                command.relationInstanceId(),
                command.sourceVersion(),
                active,
                command.sourceEventId()
        );
    }

    private LikeTaskLifecycleState likeState(TriggerLikeRemovedCommand command, boolean active) {
        return new LikeTaskLifecycleState(
                command.entityUserId(),
                command.relationKey(),
                command.relationInstanceId(),
                command.sourceVersion(),
                active,
                command.sourceEventId()
        );
    }

    private void applyTemplate(
            UUID userId,
            TaskTemplate template,
            String sourceEventId,
            String dedupAlias,
            LocalDate bizDate
    ) {
        String periodKey = taskProgressDomainService.periodKey(template.getPeriodType(), bizDate);
        if (!recordSourceEvent(userId, template.getTaskCode(), periodKey, sourceEventId, dedupAlias)) {
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
                long rewardAmount = rewardGrantDomainService.walletRewardAmount(template.getRewardBalanceDelta());
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

    private boolean recordSourceEvent(
            UUID userId,
            String taskCode,
            String periodKey,
            String sourceEventId,
            String dedupAlias
    ) {
        if (!rewardGrantDomainService.hasValidSourceEventId(sourceEventId)) {
            return false;
        }
        if (rewardGrantDomainService.hasValidSourceEventId(dedupAlias)
                && !sourceEventId.equals(dedupAlias)
                && userTaskEventLogRepository.exists(userId, taskCode, periodKey, dedupAlias.trim())) {
            return false;
        }
        UserTaskEventLogRepository.CreateStatus status = userTaskEventLogRepository.create(
                idGenerator.next(), userId, taskCode, periodKey, sourceEventId);
        return switch (status) {
            case CREATED -> true;
            case ALREADY_EXISTS -> false;
            case CONFLICT -> throw new IllegalStateException("task event log create conflict: taskCode=" + taskCode);
        };
    }

    private void ensureProgressRowExists(UUID userId, TaskTemplate template, String periodKey) {
        UserTaskProgressRepository.CreateResult result = userTaskProgressRepository.create(
                idGenerator.next(),
                userId,
                template.getTaskCode(),
                periodKey,
                template.getTargetValue(),
                STATUS_IN_PROGRESS,
                null
        );
        if (result == null || result.status() == UserTaskProgressRepository.CreateStatus.CONFLICT) {
            throw new IllegalStateException("task progress create conflict: taskCode=" + template.getTaskCode());
        }
    }

    private void rollbackLikeState(LikeTaskLifecycleState state) {
        if (state == null) {
            return;
        }
        Set<String> contributionSourceIds = new LinkedHashSet<>();
        contributionSourceIds.add(contributionSourceId(state));
        contributionSourceIds.add(state.relationKey());
        for (String contributionSourceId : contributionSourceIds) {
            rollbackLikeCreatedProgress(state.recipientUserId(), contributionSourceId);
        }
    }

    private String contributionSourceId(LikeTaskLifecycleState state) {
        return state.relationInstanceId() == null
                ? state.relationKey()
                : "like-instance:" + state.relationInstanceId();
    }

    private void rollbackLikeCreatedProgress(UUID userId, String contributionSourceId) {
        List<UserTaskEventLogRepository.UserTaskContributionLog> logs =
                userTaskEventLogRepository.findLikeContributionLogsForUpdate(userId, contributionSourceId);
        if (logs == null || logs.isEmpty()) {
            return;
        }
        for (UserTaskEventLogRepository.UserTaskContributionLog log : logs) {
            rollbackLikeContribution(log, contributionSourceId);
        }
    }

    private void rollbackLikeContribution(
            UserTaskEventLogRepository.UserTaskContributionLog log,
            String contributionSourceId
    ) {
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

        int deleted = userTaskEventLogRepository.deleteByUserTaskPeriodAndSourceEventId(
                log.userId(),
                log.taskCode(),
                log.periodKey(),
                log.sourceEventId()
        );
        if (deleted != 1) {
            return;
        }
        int remainingContributions = userTaskEventLogRepository.countByUserTaskAndPeriod(
                log.userId(), log.taskCode(), log.periodKey());
        int nextValue = Math.min(progress.getTargetValue(), Math.max(remainingContributions, 0));
        boolean stillReached = nextValue >= progress.getTargetValue();
        userTaskProgressRepository.updateProgress(
                progress.getId(),
                nextValue,
                stillReached ? STATUS_CLAIMABLE : STATUS_IN_PROGRESS,
                stillReached ? progress.getReachedAt() : null,
                null,
                null,
                contributionSourceId
        );
    }
}
