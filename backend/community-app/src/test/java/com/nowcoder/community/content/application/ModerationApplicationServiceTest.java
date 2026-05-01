package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.command.TakeModerationActionCommand;
import com.nowcoder.community.content.application.ContentModerationGateway;
import com.nowcoder.community.content.application.ModerationNoticePublisher;
import com.nowcoder.community.content.domain.model.ModerationActionRecord;
import com.nowcoder.community.content.domain.model.ModerationTarget;
import com.nowcoder.community.content.domain.model.ReportSnapshot;
import com.nowcoder.community.content.domain.model.ReportStatuses;
import com.nowcoder.community.content.domain.repository.ModerationActionRepository;
import com.nowcoder.community.content.domain.repository.ModerationTargetRepository;
import com.nowcoder.community.content.domain.repository.ReportRepository;
import com.nowcoder.community.content.domain.service.ModerationDecisionDomainService;
import com.nowcoder.community.user.api.action.UserModerationActionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Date;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ModerationApplicationServiceTest {

    private static final UUID REPORT_ID = UUID.fromString("00000000-0000-7000-8000-000000000306");
    private static final UUID ACTION_ID = UUID.fromString("00000000-0000-7000-8000-000000000307");

    private ReportRepository reportRepository;
    private ModerationActionRepository moderationActionRepository;
    private ModerationTargetRepository moderationTargetRepository;
    private ContentModerationGateway contentModerationPort;
    private ModerationNoticePublisher moderationNoticePort;
    private UserModerationActionApi userModerationActionApi;
    private ModerationApplicationService service;

    @BeforeEach
    void setUp() {
        reportRepository = mock(ReportRepository.class);
        moderationActionRepository = mock(ModerationActionRepository.class);
        moderationTargetRepository = mock(ModerationTargetRepository.class);
        contentModerationPort = mock(ContentModerationGateway.class);
        moderationNoticePort = mock(ModerationNoticePublisher.class);
        userModerationActionApi = mock(UserModerationActionApi.class);
        service = new ModerationApplicationService(
                reportRepository,
                moderationActionRepository,
                moderationTargetRepository,
                contentModerationPort,
                moderationNoticePort,
                userModerationActionApi,
                new ModerationDecisionDomainService()
        );
    }

    @Test
    void hideShouldDelegateToContentApplierAuditAndNoticePorts() {
        UUID actorId = uuid(42);
        UUID targetId = uuid(88);
        UUID targetUserId = uuid(9);
        UUID reporterId = uuid(7);
        ReportSnapshot report = pendingReport(REPORT_ID, 1, targetId, reporterId);
        ModerationActionRecord action = moderationAction(actorId, REPORT_ID, "hide", "spam", 0);
        ModerationTarget target = new ModerationTarget(1, targetId, targetUserId);
        when(reportRepository.getRequired(REPORT_ID)).thenReturn(report);
        when(moderationActionRepository.writeAction(actorId, REPORT_ID, "hide", "spam", null)).thenReturn(action);
        when(moderationTargetRepository.resolveTarget(report)).thenReturn(target);

        UUID actionId = service.takeAction(new TakeModerationActionCommand(actorId, REPORT_ID, " HIDE ", " spam ", null));

        assertThat(actionId).isEqualTo(action.id());
        InOrder inOrder = inOrder(
                reportRepository,
                moderationActionRepository,
                moderationTargetRepository,
                contentModerationPort,
                moderationNoticePort
        );
        inOrder.verify(reportRepository).getRequired(REPORT_ID);
        inOrder.verify(moderationActionRepository).writeAction(actorId, REPORT_ID, "hide", "spam", null);
        inOrder.verify(moderationTargetRepository).resolveTarget(report);
        inOrder.verify(contentModerationPort).applyContentAction(actorId, target, "hide", "spam");
        inOrder.verify(reportRepository).markStatus(REPORT_ID, ReportStatuses.PROCESSED);
        inOrder.verify(moderationNoticePort).publish(report, action, target, "to_target", targetUserId);
        inOrder.verify(moderationNoticePort).publish(report, action, target, "to_reporter", reporterId);
        verifyNoInteractions(userModerationActionApi);
    }

    @Test
    void banShouldApplyUserModerationBeforeMarkingReportProcessedAndPublishingNotices() {
        UUID actorId = uuid(42);
        UUID targetId = uuid(9);
        UUID reporterId = uuid(7);
        ReportSnapshot report = pendingReport(REPORT_ID, 3, targetId, reporterId);
        ModerationActionRecord action = moderationAction(actorId, REPORT_ID, "ban", "abuse", 3600);
        ModerationTarget target = new ModerationTarget(3, targetId, targetId);
        when(reportRepository.getRequired(REPORT_ID)).thenReturn(report);
        when(moderationActionRepository.writeAction(actorId, REPORT_ID, "ban", "abuse", 3600)).thenReturn(action);
        when(moderationTargetRepository.resolveTarget(report)).thenReturn(target);

        UUID actionId = service.takeAction(new TakeModerationActionCommand(actorId, REPORT_ID, "ban", "abuse", 3600));

        assertThat(actionId).isEqualTo(action.id());
        InOrder inOrder = inOrder(userModerationActionApi, reportRepository, moderationNoticePort);
        inOrder.verify(userModerationActionApi).applyModeration(targetId, "ban", 3600);
        inOrder.verify(reportRepository).markStatus(REPORT_ID, ReportStatuses.PROCESSED);
        inOrder.verify(moderationNoticePort).publish(report, action, target, "to_target", targetId);
        inOrder.verify(moderationNoticePort).publish(report, action, target, "to_reporter", reporterId);
    }

    @Test
    void rejectShouldOnlyMarkReportRejectedAndNotifyReporter() {
        UUID actorId = uuid(42);
        UUID targetId = uuid(88);
        UUID targetUserId = uuid(9);
        UUID reporterId = uuid(7);
        ReportSnapshot report = pendingReport(REPORT_ID, 1, targetId, reporterId);
        ModerationActionRecord action = moderationAction(actorId, REPORT_ID, "reject", "not spam", 0);
        ModerationTarget target = new ModerationTarget(1, targetId, targetUserId);
        when(reportRepository.getRequired(REPORT_ID)).thenReturn(report);
        when(moderationActionRepository.writeAction(actorId, REPORT_ID, "reject", "not spam", null)).thenReturn(action);
        when(moderationTargetRepository.resolveTarget(report)).thenReturn(target);

        UUID actionId = service.takeAction(new TakeModerationActionCommand(actorId, REPORT_ID, "reject", "not spam", null));

        assertThat(actionId).isEqualTo(action.id());
        verify(reportRepository).markStatus(REPORT_ID, ReportStatuses.REJECTED);
        verify(moderationNoticePort).publish(report, action, target, "to_reporter", reporterId);
        verifyNoInteractions(contentModerationPort, userModerationActionApi);
    }

    private ReportSnapshot pendingReport(UUID reportId, int targetType, UUID targetId, UUID reporterId) {
        return new ReportSnapshot(
                reportId,
                reporterId,
                targetType,
                targetId,
                "spam",
                "detail",
                ReportStatuses.PENDING,
                new Date()
        );
    }

    private ModerationActionRecord moderationAction(UUID actorId, UUID reportId, String action, String reason, int durationSeconds) {
        return new ModerationActionRecord(ACTION_ID, reportId, actorId, action, reason, durationSeconds, new Date());
    }
}
