package com.nowcoder.community.content.app.moderation;

import com.nowcoder.community.content.entity.ModerationAction;
import com.nowcoder.community.content.entity.Report;
import com.nowcoder.community.content.service.ReportService;
import com.nowcoder.community.user.api.action.UserModerationActionApi;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TakeModerationActionUseCaseTest {

    private static final UUID REPORT_ID = UUID.fromString("00000000-0000-7000-8000-000000000306");
    private static final UUID ACTION_ID = UUID.fromString("00000000-0000-7000-8000-000000000307");

    @Test
    void hideShouldDelegateToContentApplierAuditAndNoticeCollaborators() {
        ReportService reportService = mock(ReportService.class);
        ModerationAuditWriter moderationAuditWriter = mock(ModerationAuditWriter.class);
        ModerationTargetResolver moderationTargetResolver = mock(ModerationTargetResolver.class);
        ContentModerationApplier contentModerationApplier = mock(ContentModerationApplier.class);
        ModerationNoticePublisher moderationNoticePublisher = mock(ModerationNoticePublisher.class);
        UserModerationActionApi userModerationActionApi = mock(UserModerationActionApi.class);
        TakeModerationActionUseCase useCase = new TakeModerationActionUseCase(
                reportService,
                moderationAuditWriter,
                moderationTargetResolver,
                contentModerationApplier,
                moderationNoticePublisher,
                userModerationActionApi
        );

        UUID actorId = uuid(42);
        UUID targetId = uuid(88);
        UUID targetUserId = uuid(9);
        UUID reporterId = uuid(7);
        Report report = pendingReport(REPORT_ID, ReportService.TARGET_TYPE_POST, targetId, reporterId);
        ModerationAction action = moderationAction(actorId, REPORT_ID, "hide", "spam", 0);
        ModerationTargetResolver.ResolvedTarget target =
                new ModerationTargetResolver.ResolvedTarget(ReportService.TARGET_TYPE_POST, targetId, targetUserId);
        when(reportService.getById(REPORT_ID)).thenReturn(report);
        when(moderationAuditWriter.writeAction(actorId, REPORT_ID, "hide", "spam", null)).thenReturn(action);
        when(moderationTargetResolver.resolveTarget(report)).thenReturn(target);

        UUID actionId = useCase.takeAction(actorId, REPORT_ID, "hide", "spam", null);

        assertThat(actionId).isEqualTo(action.getId());
        verify(contentModerationApplier).applyContentAction(actorId, target, "hide", "spam");
        verify(reportService).markStatus(REPORT_ID, ReportService.STATUS_PROCESSED);
        verify(moderationNoticePublisher).publish(report, action, target, "to_target", targetUserId);
        verify(moderationNoticePublisher).publish(report, action, target, "to_reporter", reporterId);
    }

    @Test
    void banShouldApplyUserModerationBeforeMarkingReportProcessedAndPublishingNotices() {
        ReportService reportService = mock(ReportService.class);
        ModerationAuditWriter moderationAuditWriter = mock(ModerationAuditWriter.class);
        ModerationTargetResolver moderationTargetResolver = mock(ModerationTargetResolver.class);
        ContentModerationApplier contentModerationApplier = mock(ContentModerationApplier.class);
        ModerationNoticePublisher moderationNoticePublisher = mock(ModerationNoticePublisher.class);
        UserModerationActionApi userModerationActionApi = mock(UserModerationActionApi.class);
        TakeModerationActionUseCase useCase = new TakeModerationActionUseCase(
                reportService,
                moderationAuditWriter,
                moderationTargetResolver,
                contentModerationApplier,
                moderationNoticePublisher,
                userModerationActionApi
        );

        UUID actorId = uuid(42);
        UUID targetId = uuid(9);
        UUID reporterId = uuid(7);
        Report report = pendingReport(REPORT_ID, ReportService.TARGET_TYPE_USER, targetId, reporterId);
        ModerationAction action = moderationAction(actorId, REPORT_ID, "ban", "abuse", 3600);
        ModerationTargetResolver.ResolvedTarget target =
                new ModerationTargetResolver.ResolvedTarget(ReportService.TARGET_TYPE_USER, targetId, targetId);
        when(reportService.getById(REPORT_ID)).thenReturn(report);
        when(moderationAuditWriter.writeAction(actorId, REPORT_ID, "ban", "abuse", 3600)).thenReturn(action);
        when(moderationTargetResolver.resolveTarget(report)).thenReturn(target);

        UUID actionId = useCase.takeAction(actorId, REPORT_ID, "ban", "abuse", 3600);

        assertThat(actionId).isEqualTo(action.getId());
        verify(userModerationActionApi).applyModeration(targetId, "ban", 3600);
        verify(reportService).markStatus(REPORT_ID, ReportService.STATUS_PROCESSED);
        verify(moderationNoticePublisher).publish(report, action, target, "to_target", targetId);
        verify(moderationNoticePublisher).publish(report, action, target, "to_reporter", reporterId);
    }

    private Report pendingReport(UUID reportId, int targetType, UUID targetId, UUID reporterId) {
        Report report = new Report();
        report.setId(reportId);
        report.setTargetType(targetType);
        report.setTargetId(targetId);
        report.setReporterId(reporterId);
        report.setStatus(ReportService.STATUS_PENDING);
        return report;
    }

    private ModerationAction moderationAction(UUID actorId, UUID reportId, String action, String reason, Integer durationSeconds) {
        ModerationAction row = new ModerationAction();
        row.setId(ACTION_ID);
        row.setActorId(actorId);
        row.setReportId(reportId);
        row.setAction(action);
        row.setReason(reason);
        row.setDurationSeconds(durationSeconds == null ? 0 : durationSeconds);
        row.setCreateTime(new Date());
        return row;
    }
}
