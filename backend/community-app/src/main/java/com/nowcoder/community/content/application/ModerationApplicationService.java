package com.nowcoder.community.content.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.application.command.TakeModerationActionCommand;
import com.nowcoder.community.content.application.port.ContentModerationPort;
import com.nowcoder.community.content.application.port.ModerationNoticePort;
import com.nowcoder.community.content.application.result.ModerationActionResult;
import com.nowcoder.community.content.application.result.ReportModerationResult;
import com.nowcoder.community.content.domain.model.ModerationActionRecord;
import com.nowcoder.community.content.domain.model.ModerationActionSummary;
import com.nowcoder.community.content.domain.model.ModerationDecision;
import com.nowcoder.community.content.domain.model.ModerationTarget;
import com.nowcoder.community.content.domain.model.ReportSnapshot;
import com.nowcoder.community.content.domain.model.ReportStatuses;
import com.nowcoder.community.content.domain.repository.ModerationActionRepository;
import com.nowcoder.community.content.domain.repository.ModerationTargetRepository;
import com.nowcoder.community.content.domain.repository.ReportRepository;
import com.nowcoder.community.content.domain.service.ModerationDecisionDomainService;
import com.nowcoder.community.user.api.action.UserModerationActionApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class ModerationApplicationService {

    private final ReportRepository reportRepository;
    private final ModerationActionRepository moderationActionRepository;
    private final ModerationTargetRepository moderationTargetRepository;
    private final ContentModerationPort contentModerationPort;
    private final ModerationNoticePort moderationNoticePort;
    private final UserModerationActionApi userModerationActionApi;
    private final ModerationDecisionDomainService decisionDomainService;

    public ModerationApplicationService(
            ReportRepository reportRepository,
            ModerationActionRepository moderationActionRepository,
            ModerationTargetRepository moderationTargetRepository,
            ContentModerationPort contentModerationPort,
            ModerationNoticePort moderationNoticePort,
            UserModerationActionApi userModerationActionApi,
            ModerationDecisionDomainService decisionDomainService
    ) {
        this.reportRepository = reportRepository;
        this.moderationActionRepository = moderationActionRepository;
        this.moderationTargetRepository = moderationTargetRepository;
        this.contentModerationPort = contentModerationPort;
        this.moderationNoticePort = moderationNoticePort;
        this.userModerationActionApi = userModerationActionApi;
        this.decisionDomainService = decisionDomainService;
    }

    @Transactional(readOnly = true)
    public List<ReportModerationResult> listReports(Integer status, Integer targetType, UUID reporterId, Integer page, Integer size) {
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? 20 : Math.min(100, Math.max(1, size));
        return reportRepository.listReports(status, targetType, reporterId, p, s).stream()
                .map(this::toReportResult)
                .toList();
    }

    @Transactional
    public UUID takeAction(TakeModerationActionCommand command) {
        ModerationDecision decision = decisionDomainService.decide(
                command.actorId(),
                command.reportId(),
                command.action(),
                command.reason(),
                command.durationSeconds()
        );
        ReportSnapshot report = reportRepository.getRequired(decision.reportId());
        if (report.status() != ReportStatuses.PENDING) {
            throw new BusinessException(INVALID_ARGUMENT, "该举报已处理");
        }

        ModerationActionRecord action = moderationActionRepository.writeAction(
                decision.actorId(),
                decision.reportId(),
                decision.normalizedAction(),
                decision.normalizedReason(),
                decision.auditDurationSeconds()
        );
        ModerationTarget target = moderationTargetRepository.resolveTarget(report);

        if (decision.isReject()) {
            reportRepository.markStatus(decision.reportId(), ReportStatuses.REJECTED);
            moderationNoticePort.publish(report, action, target, "to_reporter", report.reporterId());
            return action.id();
        }
        if (decision.isContentAction()) {
            contentModerationPort.applyContentAction(decision.actorId(), target, decision.normalizedAction(), decision.normalizedReason());
            markProcessedAndNotify(report, action, target);
            return action.id();
        }
        if (decision.isWarn()) {
            markProcessedAndNotify(report, action, target);
            return action.id();
        }
        if (decision.isUserModerationAction()) {
            userModerationActionApi.applyModeration(target.targetUserId(), decision.normalizedAction(), decision.resolvedDurationSeconds());
            markProcessedAndNotify(report, action, target);
            return action.id();
        }
        throw new BusinessException(INVALID_ARGUMENT, "未支持的 action");
    }

    @Transactional(readOnly = true)
    public List<ModerationActionResult> listActions(UUID actorId, Integer page, Integer size) {
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? 20 : Math.min(100, Math.max(1, size));
        return moderationActionRepository.listActions(actorId, p, s).stream()
                .map(this::toActionResult)
                .toList();
    }

    private void markProcessedAndNotify(ReportSnapshot report, ModerationActionRecord action, ModerationTarget target) {
        reportRepository.markStatus(report.id(), ReportStatuses.PROCESSED);
        moderationNoticePort.publish(report, action, target, "to_target", target.targetUserId());
        moderationNoticePort.publish(report, action, target, "to_reporter", report.reporterId());
    }

    private ReportModerationResult toReportResult(ReportSnapshot report) {
        return new ReportModerationResult(
                report.id(),
                report.reporterId(),
                report.targetType(),
                report.targetId(),
                report.reason(),
                report.detail(),
                report.status(),
                report.createTime()
        );
    }

    private ModerationActionResult toActionResult(ModerationActionSummary action) {
        return new ModerationActionResult(
                action.id(),
                action.reportId(),
                action.actorId(),
                action.action(),
                action.reason(),
                action.durationSeconds(),
                action.createTime()
        );
    }
}
