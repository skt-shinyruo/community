package com.nowcoder.community.growth.service;

import com.nowcoder.community.growth.entity.TaskTemplate;
import com.nowcoder.community.growth.entity.UserTaskProgress;
import com.nowcoder.community.growth.mapper.TaskTemplateMapper;
import com.nowcoder.community.growth.mapper.UserTaskEventLogMapper;
import com.nowcoder.community.growth.mapper.UserTaskProgressMapper;
import com.nowcoder.community.wallet.api.action.WalletRewardActionApi;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

@Service
public class TaskProgressService {

    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_CLAIMABLE = "CLAIMABLE";
    private static final String STATUS_CLAIMED = "CLAIMED";
    private final TaskTemplateMapper taskTemplateMapper;
    private final UserTaskProgressMapper userTaskProgressMapper;
    private final UserTaskEventLogMapper userTaskEventLogMapper;
    private final WalletRewardActionApi walletRewardActionApi;
    private final GrowthBusinessTimeService growthBusinessTimeService;

    public TaskProgressService(
            TaskTemplateMapper taskTemplateMapper,
            UserTaskProgressMapper userTaskProgressMapper,
            UserTaskEventLogMapper userTaskEventLogMapper,
            WalletRewardActionApi walletRewardActionApi,
            GrowthBusinessTimeService growthBusinessTimeService
    ) {
        this.taskTemplateMapper = taskTemplateMapper;
        this.userTaskProgressMapper = userTaskProgressMapper;
        this.userTaskEventLogMapper = userTaskEventLogMapper;
        this.walletRewardActionApi = walletRewardActionApi;
        this.growthBusinessTimeService = growthBusinessTimeService;
    }

    @Transactional
    public void processEvent(int userId, String triggerEventType, String sourceEventId, LocalDate bizDate) {
        if (userId <= 0 || triggerEventType == null || triggerEventType.isBlank() || sourceEventId == null || sourceEventId.isBlank() || bizDate == null) {
            return;
        }
        List<TaskTemplate> templates = taskTemplateMapper.selectActiveByTriggerEventType(triggerEventType.trim());
        for (TaskTemplate template : templates) {
            if (template == null || template.getTargetValue() <= 0) {
                continue;
            }
            applyTemplate(userId, template, sourceEventId.trim(), bizDate);
        }
    }

    private void applyTemplate(int userId, TaskTemplate template, String sourceEventId, LocalDate bizDate) {
        String periodKey = TaskPeriodKeyResolver.resolve(template.getPeriodType(), bizDate);
        if (!recordSourceEvent(userId, template.getTaskCode(), periodKey, sourceEventId)) {
            return;
        }
        ensureProgressRowExists(userId, template, periodKey);
        UserTaskProgress progress = userTaskProgressMapper.selectByUserTaskAndPeriodForUpdate(userId, template.getTaskCode(), periodKey);
        if (progress == null) {
            throw new IllegalStateException("task progress init failed: taskCode=" + template.getTaskCode());
        }
        if (STATUS_CLAIMED.equals(progress.getStatus()) && progress.getRewardGrantId() != null) {
            return;
        }

        int nextValue = Math.min(template.getTargetValue(), progress.getCurrentValue() + 1);
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
                rewardGrantId = "task:" + userId + ":" + template.getTaskCode() + ":" + periodKey;
                long rewardAmount = (long) template.getRewardGrowthDelta() + template.getRewardBalanceDelta();
                if (rewardAmount > 0) {
                    walletRewardActionApi.issue(rewardGrantId, userId, rewardAmount, template.getTaskCode());
                }
                nextStatus = STATUS_CLAIMED;
                claimedAt = now;
            } else {
                nextStatus = STATUS_CLAIMED;
            }
        }

        userTaskProgressMapper.updateProgress(
                progress.getId(),
                nextValue,
                nextStatus,
                reachedAt,
                claimedAt,
                rewardGrantId,
                sourceEventId
        );
    }

    private boolean recordSourceEvent(int userId, String taskCode, String periodKey, String sourceEventId) {
        try {
            userTaskEventLogMapper.insert(userId, taskCode, periodKey, sourceEventId);
            return true;
        } catch (DataIntegrityViolationException ignored) {
            return false;
        }
    }

    private void ensureProgressRowExists(int userId, TaskTemplate template, String periodKey) {
        try {
            userTaskProgressMapper.insert(userId, template.getTaskCode(), periodKey, template.getTargetValue(), STATUS_IN_PROGRESS, null);
        } catch (DataIntegrityViolationException ignored) {
            // Another event created the row first; lock and continue.
        }
    }
}
