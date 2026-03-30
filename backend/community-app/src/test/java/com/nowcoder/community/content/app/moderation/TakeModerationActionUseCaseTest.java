package com.nowcoder.community.content.app.moderation;

import com.nowcoder.community.content.entity.ModerationAction;
import com.nowcoder.community.content.entity.Report;
import com.nowcoder.community.content.service.ReportService;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TakeModerationActionUseCaseTest {

    @Test
    void hideShouldDelegateToContentApplierAuditAndNoticeCollaborators() {
        ReportService reportService = mock(ReportService.class);
        ModerationAuditWriter moderationAuditWriter = mock(ModerationAuditWriter.class);
        ModerationTargetResolver moderationTargetResolver = mock(ModerationTargetResolver.class);
        ContentModerationApplier contentModerationApplier = mock(ContentModerationApplier.class);
        ModerationNoticePublisher moderationNoticePublisher = mock(ModerationNoticePublisher.class);
        UserModerationCommandPublisher userModerationCommandPublisher = mock(UserModerationCommandPublisher.class);
        TakeModerationActionUseCase useCase = new TakeModerationActionUseCase(
                reportService,
                moderationAuditWriter,
                moderationTargetResolver,
                contentModerationApplier,
                moderationNoticePublisher,
                userModerationCommandPublisher
        );

        Report report = pendingReport(12, ReportService.TARGET_TYPE_POST, 88, 7);
        ModerationAction action = moderationAction(42, 12, "hide", "spam", 0);
        ModerationTargetResolver.ResolvedTarget target =
                new ModerationTargetResolver.ResolvedTarget(ReportService.TARGET_TYPE_POST, 88, 9);
        when(reportService.getById(12)).thenReturn(report);
        when(moderationAuditWriter.writeAction(42, 12, "hide", "spam", null)).thenReturn(action);
        when(moderationTargetResolver.resolveTarget(report)).thenReturn(target);

        int actionId = useCase.takeAction(42, 12, "hide", "spam", null);

        assertThat(actionId).isEqualTo(action.getId());
        verify(contentModerationApplier).applyContentAction(42, target, "hide", "spam");
        verify(reportService).markStatus(12, ReportService.STATUS_PROCESSED);
        verify(moderationNoticePublisher).publish(report, action, target, "to_target", 9);
        verify(moderationNoticePublisher).publish(report, action, target, "to_reporter", 7);
    }

    @Test
    void banShouldDelegateToModerationCommandPublisherAndNotices() {
        ReportService reportService = mock(ReportService.class);
        ModerationAuditWriter moderationAuditWriter = mock(ModerationAuditWriter.class);
        ModerationTargetResolver moderationTargetResolver = mock(ModerationTargetResolver.class);
        ContentModerationApplier contentModerationApplier = mock(ContentModerationApplier.class);
        ModerationNoticePublisher moderationNoticePublisher = mock(ModerationNoticePublisher.class);
        UserModerationCommandPublisher userModerationCommandPublisher = mock(UserModerationCommandPublisher.class);
        TakeModerationActionUseCase useCase = new TakeModerationActionUseCase(
                reportService,
                moderationAuditWriter,
                moderationTargetResolver,
                contentModerationApplier,
                moderationNoticePublisher,
                userModerationCommandPublisher
        );

        Report report = pendingReport(12, ReportService.TARGET_TYPE_USER, 9, 7);
        ModerationAction action = moderationAction(42, 12, "ban", "abuse", 3600);
        ModerationTargetResolver.ResolvedTarget target =
                new ModerationTargetResolver.ResolvedTarget(ReportService.TARGET_TYPE_USER, 9, 9);
        when(reportService.getById(12)).thenReturn(report);
        when(moderationAuditWriter.writeAction(42, 12, "ban", "abuse", 3600)).thenReturn(action);
        when(moderationTargetResolver.resolveTarget(report)).thenReturn(target);

        int actionId = useCase.takeAction(42, 12, "ban", "abuse", 3600);

        assertThat(actionId).isEqualTo(action.getId());
        verify(userModerationCommandPublisher).publishModerationCommand(42, 12, 9, "ban", 3600, "abuse");
        verify(reportService).markStatus(12, ReportService.STATUS_PROCESSED);
        verify(moderationNoticePublisher).publish(report, action, target, "to_target", 9);
        verify(moderationNoticePublisher).publish(report, action, target, "to_reporter", 7);
    }

    private Report pendingReport(int reportId, int targetType, int targetId, int reporterId) {
        Report report = new Report();
        report.setId(reportId);
        report.setTargetType(targetType);
        report.setTargetId(targetId);
        report.setReporterId(reporterId);
        report.setStatus(ReportService.STATUS_PENDING);
        return report;
    }

    private ModerationAction moderationAction(int actorId, int reportId, String action, String reason, Integer durationSeconds) {
        ModerationAction row = new ModerationAction();
        row.setId(21);
        row.setActorId(actorId);
        row.setReportId(reportId);
        row.setAction(action);
        row.setReason(reason);
        row.setDurationSeconds(durationSeconds == null ? 0 : durationSeconds);
        row.setCreateTime(new Date());
        return row;
    }
}
