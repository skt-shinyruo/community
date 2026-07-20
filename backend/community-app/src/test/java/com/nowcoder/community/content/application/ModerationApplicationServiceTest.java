package com.nowcoder.community.content.application;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.ErrorKind;
import com.nowcoder.community.content.application.command.TakeModerationActionCommand;
import com.nowcoder.community.content.application.ModerationNoticePublisher;
import com.nowcoder.community.content.domain.model.ModerationActionRecord;
import com.nowcoder.community.content.domain.model.ModerationTarget;
import com.nowcoder.community.content.domain.model.ReportSnapshot;
import com.nowcoder.community.content.domain.model.ReportStatuses;
import com.nowcoder.community.content.domain.repository.ModerationActionRepository;
import com.nowcoder.community.content.domain.repository.ModerationTargetRepository;
import com.nowcoder.community.content.domain.repository.ReportRepository;
import com.nowcoder.community.content.domain.service.ModerationDecisionDomainService;
import com.nowcoder.community.content.exception.ContentErrorCode;
import com.nowcoder.community.user.api.action.UserModerationActionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class ModerationApplicationServiceTest {

    private static final UUID REPORT_ID = UUID.fromString("00000000-0000-7000-8000-000000000306");
    private static final UUID ACTION_ID = UUID.fromString("00000000-0000-7000-8000-000000000307");

    private ReportRepository reportRepository;
    private ModerationActionRepository moderationActionRepository;
    private ModerationTargetRepository moderationTargetRepository;
    private PostModerationApplicationService postModerationApplicationService;
    private CommentApplicationService commentApplicationService;
    private ModerationNoticePublisher moderationNoticePort;
    private UserModerationActionApi userModerationActionApi;
    private ModerationApplicationService service;

    @BeforeEach
    void setUp() {
        reportRepository = mock(ReportRepository.class);
        moderationActionRepository = mock(ModerationActionRepository.class);
        moderationTargetRepository = mock(ModerationTargetRepository.class);
        postModerationApplicationService = mock(PostModerationApplicationService.class);
        commentApplicationService = mock(CommentApplicationService.class);
        moderationNoticePort = mock(ModerationNoticePublisher.class);
        userModerationActionApi = mock(UserModerationActionApi.class);
        service = new ModerationApplicationService(
                reportRepository,
                moderationActionRepository,
                moderationTargetRepository,
                postModerationApplicationService,
                commentApplicationService,
                moderationNoticePort,
                userModerationActionApi,
                new ModerationDecisionDomainService()
        );
    }

    @Test
    void claimWinnerShouldApplyPostModerationThenWriteActionTransitionAndPublishNotices() {
        UUID actorId = uuid(42);
        UUID targetId = uuid(88);
        UUID targetUserId = uuid(9);
        UUID reporterId = uuid(7);
        ReportSnapshot report = processingReport(REPORT_ID, EntityTypes.POST, targetId, reporterId);
        ModerationActionRecord action = moderationAction(actorId, REPORT_ID, "hide", "spam", 0);
        ModerationTarget target = new ModerationTarget(EntityTypes.POST, targetId, targetUserId);
        when(reportRepository.claimPending(REPORT_ID)).thenReturn(true);
        when(reportRepository.getRequired(REPORT_ID)).thenReturn(report);
        when(moderationActionRepository.writeAction(actorId, REPORT_ID, "hide", "spam", null)).thenReturn(action);
        when(moderationTargetRepository.resolveTarget(report)).thenReturn(target);
        when(reportRepository.transitionStatus(REPORT_ID, ReportStatuses.PROCESSING, ReportStatuses.PROCESSED))
                .thenReturn(true);

        UUID actionId = service.takeAction(new TakeModerationActionCommand(actorId, REPORT_ID, " HIDE ", " spam ", null));

        assertThat(actionId).isEqualTo(action.id());
        InOrder inOrder = inOrder(
                reportRepository,
                moderationActionRepository,
                moderationTargetRepository,
                postModerationApplicationService,
                moderationNoticePort
        );
        inOrder.verify(reportRepository).claimPending(REPORT_ID);
        inOrder.verify(reportRepository).getRequired(REPORT_ID);
        inOrder.verify(moderationTargetRepository).resolveTarget(report);
        inOrder.verify(postModerationApplicationService).deleteByModeration(actorId, targetId);
        inOrder.verify(moderationActionRepository).writeAction(actorId, REPORT_ID, "hide", "spam", null);
        inOrder.verify(reportRepository)
                .transitionStatus(REPORT_ID, ReportStatuses.PROCESSING, ReportStatuses.PROCESSED);
        inOrder.verify(moderationNoticePort).publish(report, action, target, "to_target", targetUserId);
        inOrder.verify(moderationNoticePort).publish(report, action, target, "to_reporter", reporterId);
        verifyNoInteractions(userModerationActionApi);
    }

    @Test
    void takeActionShouldRejectNullCommand() {
        assertThatThrownBy(() -> service.takeAction(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void claimLoserShouldReplaySameNormalizedDecisionWithoutExecutingSideEffects() {
        UUID firstActorId = uuid(41);
        UUID replayActorId = uuid(42);
        ModerationActionRecord existingAction = moderationAction(
                firstActorId,
                REPORT_ID,
                "ban",
                "abuse",
                7 * 24 * 3600
        );
        when(reportRepository.claimPending(REPORT_ID)).thenReturn(false);
        when(moderationActionRepository.findByReportId(REPORT_ID)).thenReturn(Optional.of(existingAction));

        UUID actionId = service.takeAction(new TakeModerationActionCommand(
                replayActorId,
                REPORT_ID,
                " BAN ",
                " abuse ",
                null
        ));

        assertThat(actionId).isEqualTo(existingAction.id());
        verify(moderationActionRepository).findByReportId(REPORT_ID);
        verifyNoInteractions(
                moderationTargetRepository,
                postModerationApplicationService,
                commentApplicationService,
                moderationNoticePort,
                userModerationActionApi
        );
    }

    @Test
    void claimLoserShouldConflictWhenAnyNormalizedDecisionFieldDiffers() {
        UUID firstActorId = uuid(41);
        UUID replayActorId = uuid(42);
        ModerationActionRecord existingAction = moderationAction(
                firstActorId,
                REPORT_ID,
                "ban",
                "abuse",
                7 * 24 * 3600
        );
        when(reportRepository.claimPending(REPORT_ID)).thenReturn(false);
        when(moderationActionRepository.findByReportId(REPORT_ID)).thenReturn(Optional.of(existingAction));

        assertDecisionConflict(new TakeModerationActionCommand(replayActorId, REPORT_ID, "mute", "abuse", null));
        assertDecisionConflict(new TakeModerationActionCommand(replayActorId, REPORT_ID, "ban", "other reason", null));
        assertDecisionConflict(new TakeModerationActionCommand(replayActorId, REPORT_ID, "ban", "abuse", 3600));

        assertThat(ContentErrorCode.MODERATION_DECISION_CONFLICT.getKind()).isEqualTo(ErrorKind.CONFLICT);
        verify(moderationActionRepository, never()).writeAction(any(), any(), any(), any(), any());
        verifyNoInteractions(
                moderationTargetRepository,
                postModerationApplicationService,
                commentApplicationService,
                moderationNoticePort,
                userModerationActionApi
        );
    }

    @Test
    void claimLoserShouldConflictWhenNoActionIsVisible() {
        when(reportRepository.claimPending(REPORT_ID)).thenReturn(false);
        when(moderationActionRepository.findByReportId(REPORT_ID)).thenReturn(Optional.empty());

        assertDecisionConflict(new TakeModerationActionCommand(
                uuid(42),
                REPORT_ID,
                "ban",
                "abuse",
                3600
        ));

        verify(moderationActionRepository, never()).writeAction(any(), any(), any(), any(), any());
        verifyNoInteractions(
                moderationTargetRepository,
                postModerationApplicationService,
                commentApplicationService,
                moderationNoticePort,
                userModerationActionApi
        );
    }

    @Test
    void terminalTransitionMissShouldFailInternalAndSkipNotices() {
        UUID actorId = uuid(42);
        UUID targetId = uuid(9);
        UUID reporterId = uuid(7);
        ReportSnapshot report = processingReport(REPORT_ID, EntityTypes.USER, targetId, reporterId);
        ModerationActionRecord action = moderationAction(actorId, REPORT_ID, "ban", "abuse", 3600);
        ModerationTarget target = new ModerationTarget(EntityTypes.USER, targetId, targetId);
        when(reportRepository.claimPending(REPORT_ID)).thenReturn(true);
        when(reportRepository.getRequired(REPORT_ID)).thenReturn(report);
        when(moderationTargetRepository.resolveTarget(report)).thenReturn(target);
        when(moderationActionRepository.writeAction(actorId, REPORT_ID, "ban", "abuse", 3600)).thenReturn(action);
        when(reportRepository.transitionStatus(REPORT_ID, ReportStatuses.PROCESSING, ReportStatuses.PROCESSED))
                .thenReturn(false);

        assertThatThrownBy(() -> service.takeAction(
                new TakeModerationActionCommand(actorId, REPORT_ID, "ban", "abuse", 3600)
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ContentErrorCode.INTERNAL_ERROR));

        verify(userModerationActionApi).applyModeration(targetId, "ban", 3600);
        verify(moderationActionRepository).writeAction(actorId, REPORT_ID, "ban", "abuse", 3600);
        verifyNoInteractions(moderationNoticePort);
    }

    @Test
    void hideCommentShouldApplyCommentModerationAuditAndNoticePorts() {
        UUID actorId = uuid(42);
        UUID targetId = uuid(88);
        UUID targetUserId = uuid(9);
        UUID reporterId = uuid(7);
        ReportSnapshot report = processingReport(REPORT_ID, EntityTypes.COMMENT, targetId, reporterId);
        ModerationActionRecord action = moderationAction(actorId, REPORT_ID, "hide", "spam", 0);
        ModerationTarget target = new ModerationTarget(EntityTypes.COMMENT, targetId, targetUserId);
        when(reportRepository.claimPending(REPORT_ID)).thenReturn(true);
        when(reportRepository.getRequired(REPORT_ID)).thenReturn(report);
        when(moderationActionRepository.writeAction(actorId, REPORT_ID, "hide", "spam", null)).thenReturn(action);
        when(moderationTargetRepository.resolveTarget(report)).thenReturn(target);
        when(reportRepository.transitionStatus(REPORT_ID, ReportStatuses.PROCESSING, ReportStatuses.PROCESSED))
                .thenReturn(true);

        UUID actionId = service.takeAction(new TakeModerationActionCommand(actorId, REPORT_ID, "hide", "spam", null));

        assertThat(actionId).isEqualTo(action.id());
        InOrder inOrder = inOrder(reportRepository, moderationActionRepository, moderationTargetRepository, commentApplicationService, moderationNoticePort);
        inOrder.verify(reportRepository).claimPending(REPORT_ID);
        inOrder.verify(reportRepository).getRequired(REPORT_ID);
        inOrder.verify(moderationTargetRepository).resolveTarget(report);
        inOrder.verify(commentApplicationService).deleteByModeration(actorId, targetId, "hide: spam");
        inOrder.verify(moderationActionRepository).writeAction(actorId, REPORT_ID, "hide", "spam", null);
        inOrder.verify(reportRepository)
                .transitionStatus(REPORT_ID, ReportStatuses.PROCESSING, ReportStatuses.PROCESSED);
        inOrder.verify(moderationNoticePort).publish(report, action, target, "to_target", targetUserId);
        inOrder.verify(moderationNoticePort).publish(report, action, target, "to_reporter", reporterId);
        verifyNoInteractions(userModerationActionApi);
    }

    @Test
    void banShouldApplyUserModerationBeforeMarkingReportProcessedAndPublishingNotices() {
        UUID actorId = uuid(42);
        UUID targetId = uuid(9);
        UUID reporterId = uuid(7);
        ReportSnapshot report = processingReport(REPORT_ID, 3, targetId, reporterId);
        ModerationActionRecord action = moderationAction(actorId, REPORT_ID, "ban", "abuse", 3600);
        ModerationTarget target = new ModerationTarget(3, targetId, targetId);
        when(reportRepository.claimPending(REPORT_ID)).thenReturn(true);
        when(reportRepository.getRequired(REPORT_ID)).thenReturn(report);
        when(moderationActionRepository.writeAction(actorId, REPORT_ID, "ban", "abuse", 3600)).thenReturn(action);
        when(moderationTargetRepository.resolveTarget(report)).thenReturn(target);
        when(reportRepository.transitionStatus(REPORT_ID, ReportStatuses.PROCESSING, ReportStatuses.PROCESSED))
                .thenReturn(true);

        UUID actionId = service.takeAction(new TakeModerationActionCommand(actorId, REPORT_ID, "ban", "abuse", 3600));

        assertThat(actionId).isEqualTo(action.id());
        InOrder inOrder = inOrder(
                reportRepository,
                moderationTargetRepository,
                userModerationActionApi,
                moderationActionRepository,
                moderationNoticePort
        );
        inOrder.verify(reportRepository).claimPending(REPORT_ID);
        inOrder.verify(reportRepository).getRequired(REPORT_ID);
        inOrder.verify(moderationTargetRepository).resolveTarget(report);
        inOrder.verify(userModerationActionApi).applyModeration(targetId, "ban", 3600);
        inOrder.verify(moderationActionRepository).writeAction(actorId, REPORT_ID, "ban", "abuse", 3600);
        inOrder.verify(reportRepository)
                .transitionStatus(REPORT_ID, ReportStatuses.PROCESSING, ReportStatuses.PROCESSED);
        inOrder.verify(moderationNoticePort).publish(report, action, target, "to_target", targetId);
        inOrder.verify(moderationNoticePort).publish(report, action, target, "to_reporter", reporterId);
    }

    @Test
    void rejectShouldOnlyMarkReportRejectedAndNotifyReporter() {
        UUID actorId = uuid(42);
        UUID targetId = uuid(88);
        UUID targetUserId = uuid(9);
        UUID reporterId = uuid(7);
        ReportSnapshot report = processingReport(REPORT_ID, 1, targetId, reporterId);
        ModerationActionRecord action = moderationAction(actorId, REPORT_ID, "reject", "not spam", 0);
        ModerationTarget target = new ModerationTarget(1, targetId, targetUserId);
        when(reportRepository.claimPending(REPORT_ID)).thenReturn(true);
        when(reportRepository.getRequired(REPORT_ID)).thenReturn(report);
        when(moderationActionRepository.writeAction(actorId, REPORT_ID, "reject", "not spam", null)).thenReturn(action);
        when(moderationTargetRepository.resolveTarget(report)).thenReturn(target);
        when(reportRepository.transitionStatus(REPORT_ID, ReportStatuses.PROCESSING, ReportStatuses.REJECTED))
                .thenReturn(true);

        UUID actionId = service.takeAction(new TakeModerationActionCommand(actorId, REPORT_ID, "reject", "not spam", null));

        assertThat(actionId).isEqualTo(action.id());
        verify(reportRepository).transitionStatus(REPORT_ID, ReportStatuses.PROCESSING, ReportStatuses.REJECTED);
        verify(moderationNoticePort).publish(report, action, target, "to_reporter", reporterId);
        verifyNoInteractions(postModerationApplicationService, commentApplicationService, userModerationActionApi);
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

    private void assertDecisionConflict(TakeModerationActionCommand command) {
        assertThatThrownBy(() -> service.takeAction(command))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ContentErrorCode.MODERATION_DECISION_CONFLICT));
    }

    private ReportSnapshot processingReport(UUID reportId, int targetType, UUID targetId, UUID reporterId) {
        return new ReportSnapshot(
                reportId,
                reporterId,
                targetType,
                targetId,
                "spam",
                "detail",
                ReportStatuses.PROCESSING,
                new Date()
        );
    }

    private ModerationActionRecord moderationAction(UUID actorId, UUID reportId, String action, String reason, int durationSeconds) {
        return new ModerationActionRecord(ACTION_ID, reportId, actorId, action, reason, durationSeconds, new Date());
    }
}
