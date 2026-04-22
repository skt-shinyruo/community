package com.nowcoder.community.content.app.moderation;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.entity.ModerationAction;
import com.nowcoder.community.content.entity.Report;
import com.nowcoder.community.content.service.ModerationService;
import com.nowcoder.community.content.service.ReportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class TakeModerationActionUseCase {

    private static final int DEFAULT_MUTE_SECONDS = 24 * 3600;
    private static final int DEFAULT_BAN_SECONDS = 7 * 24 * 3600;

    private final ReportService reportService;
    private final ModerationAuditWriter moderationAuditWriter;
    private final ModerationTargetResolver moderationTargetResolver;
    private final ContentModerationApplier contentModerationApplier;
    private final ModerationNoticePublisher moderationNoticePublisher;
    private final UserModerationCommandPublisher userModerationCommandPublisher;

    public TakeModerationActionUseCase(
            ReportService reportService,
            ModerationAuditWriter moderationAuditWriter,
            ModerationTargetResolver moderationTargetResolver,
            ContentModerationApplier contentModerationApplier,
            ModerationNoticePublisher moderationNoticePublisher,
            UserModerationCommandPublisher userModerationCommandPublisher
    ) {
        this.reportService = reportService;
        this.moderationAuditWriter = moderationAuditWriter;
        this.moderationTargetResolver = moderationTargetResolver;
        this.contentModerationApplier = contentModerationApplier;
        this.moderationNoticePublisher = moderationNoticePublisher;
        this.userModerationCommandPublisher = userModerationCommandPublisher;
    }

    @Transactional
    public UUID takeAction(UUID actorId, UUID reportId, String action, String reason, Integer durationSeconds) {
        if (actorId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorId 非法");
        }
        if (reportId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "reportId 非法");
        }
        String normalizedAction = safeLower(action);
        if (!isSupportedAction(normalizedAction)) {
            throw new BusinessException(INVALID_ARGUMENT, "action 非法");
        }
        String normalizedReason = safeTrim(reason);
        if (!StringUtils.hasText(normalizedReason)) {
            throw new BusinessException(INVALID_ARGUMENT, "reason 不能为空");
        }

        Report report = reportService.getById(reportId);
        if (report.getStatus() != ReportService.STATUS_PENDING) {
            throw new BusinessException(INVALID_ARGUMENT, "该举报已处理");
        }

        int resolvedDuration = resolveDuration(normalizedAction, durationSeconds);
        ModerationAction actionRow = moderationAuditWriter.writeAction(actorId, reportId, normalizedAction, normalizedReason, resolvedDuration == 0 ? null : resolvedDuration);
        if (resolvedDuration > 0) {
            actionRow.setDurationSeconds(resolvedDuration);
        }
        ModerationTargetResolver.ResolvedTarget target = moderationTargetResolver.resolveTarget(report);

        if (ModerationService.ACTION_REJECT.equals(normalizedAction)) {
            reportService.markStatus(reportId, ReportService.STATUS_REJECTED);
            moderationNoticePublisher.publish(report, actionRow, target, "to_reporter", report.getReporterId());
            return actionRow.getId();
        }

        if (ModerationService.ACTION_HIDE.equals(normalizedAction) || ModerationService.ACTION_DELETE.equals(normalizedAction)) {
            contentModerationApplier.applyContentAction(actorId, target, normalizedAction, normalizedReason);
            reportService.markStatus(reportId, ReportService.STATUS_PROCESSED);
            moderationNoticePublisher.publish(report, actionRow, target, "to_target", target.targetUserId());
            moderationNoticePublisher.publish(report, actionRow, target, "to_reporter", report.getReporterId());
            return actionRow.getId();
        }

        if (ModerationService.ACTION_WARN.equals(normalizedAction)) {
            reportService.markStatus(reportId, ReportService.STATUS_PROCESSED);
            moderationNoticePublisher.publish(report, actionRow, target, "to_target", target.targetUserId());
            moderationNoticePublisher.publish(report, actionRow, target, "to_reporter", report.getReporterId());
            return actionRow.getId();
        }

        if (ModerationService.ACTION_MUTE.equals(normalizedAction) || ModerationService.ACTION_BAN.equals(normalizedAction)) {
            userModerationCommandPublisher.publishModerationCommand(actorId, reportId, target.targetUserId(), normalizedAction, resolvedDuration, normalizedReason);
            reportService.markStatus(reportId, ReportService.STATUS_PROCESSED);
            moderationNoticePublisher.publish(report, actionRow, target, "to_target", target.targetUserId());
            moderationNoticePublisher.publish(report, actionRow, target, "to_reporter", report.getReporterId());
            return actionRow.getId();
        }

        throw new BusinessException(INVALID_ARGUMENT, "未支持的 action");
    }

    private int resolveDuration(String action, Integer durationSeconds) {
        if (ModerationService.ACTION_MUTE.equals(action)) {
            return durationSeconds == null || durationSeconds <= 0 ? DEFAULT_MUTE_SECONDS : durationSeconds;
        }
        if (ModerationService.ACTION_BAN.equals(action)) {
            return durationSeconds == null || durationSeconds <= 0 ? DEFAULT_BAN_SECONDS : durationSeconds;
        }
        return durationSeconds == null ? 0 : Math.max(0, durationSeconds);
    }

    private boolean isSupportedAction(String action) {
        return ModerationService.ACTION_REJECT.equals(action)
                || ModerationService.ACTION_HIDE.equals(action)
                || ModerationService.ACTION_DELETE.equals(action)
                || ModerationService.ACTION_WARN.equals(action)
                || ModerationService.ACTION_MUTE.equals(action)
                || ModerationService.ACTION_BAN.equals(action);
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeLower(String value) {
        return safeTrim(value).toLowerCase();
    }
}
