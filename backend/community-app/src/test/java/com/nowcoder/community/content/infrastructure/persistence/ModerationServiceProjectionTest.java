package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.application.ModerationApplicationService;
import com.nowcoder.community.content.application.ModerationApplicationService.ModerationActionResult;
import com.nowcoder.community.content.application.ModerationApplicationService.ReportModerationResult;
import com.nowcoder.community.content.domain.model.ModerationActionRecord;
import com.nowcoder.community.content.domain.model.ReportSnapshot;
import com.nowcoder.community.content.domain.repository.ModerationActionRepository;
import com.nowcoder.community.content.domain.repository.ReportRepository;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModerationServiceProjectionTest {

    private static final UUID REPORT_ID = UUID.fromString("00000000-0000-7000-8000-000000000302");
    private static final UUID ACTION_ID = UUID.fromString("00000000-0000-7000-8000-000000000303");
    private static final UUID REPORTER_ID = uuid(7);
    private static final UUID TARGET_ID = uuid(88);
    private static final UUID ACTOR_ID = uuid(99);

    @Test
    void listReportsShouldReturnReportModels() {
        ReportRepository reportRepository = mock(ReportRepository.class);
        Date createTime = new Date();
        when(reportRepository.listReports(0, 1, REPORTER_ID, 0, 20)).thenReturn(List.of(new ReportSnapshot(
                REPORT_ID,
                REPORTER_ID,
                1,
                TARGET_ID,
                "spam",
                "details",
                0,
                createTime
        )));
        ModerationApplicationService service = service(reportRepository, mock(ModerationActionRepository.class));

        ReportModerationResult response = service.listReports(0, 1, REPORTER_ID, 0, 20).get(0);

        assertThat(response.id()).isEqualTo(REPORT_ID);
        assertThat(response.reporterId()).isEqualTo(REPORTER_ID);
        assertThat(response.targetType()).isEqualTo(1);
        assertThat(response.targetId()).isEqualTo(TARGET_ID);
        assertThat(response.reason()).isEqualTo("spam");
        assertThat(response.detail()).isEqualTo("details");
        assertThat(response.status()).isEqualTo(0);
    }

    @Test
    void listActionsShouldReturnModerationActionModels() {
        ModerationActionRepository actionRepository = mock(ModerationActionRepository.class);
        when(actionRepository.listActions(ACTOR_ID, 0, 20)).thenReturn(List.of(new ModerationActionRecord(
                ACTION_ID,
                REPORT_ID,
                ACTOR_ID,
                "ban",
                "abuse",
                3600,
                new Date()
        )));
        ModerationApplicationService service = service(mock(ReportRepository.class), actionRepository);

        ModerationActionResult response = service.listActions(ACTOR_ID, 0, 20).get(0);

        assertThat(response.id()).isEqualTo(ACTION_ID);
        assertThat(response.reportId()).isEqualTo(REPORT_ID);
        assertThat(response.actorId()).isEqualTo(ACTOR_ID);
        assertThat(response.action()).isEqualTo("ban");
        assertThat(response.reason()).isEqualTo("abuse");
        assertThat(response.durationSeconds()).isEqualTo(3600);
    }

    private static ModerationApplicationService service(
            ReportRepository reportRepository,
            ModerationActionRepository actionRepository
    ) {
        return new ModerationApplicationService(
                reportRepository,
                actionRepository,
                mock(com.nowcoder.community.content.domain.repository.ModerationTargetRepository.class),
                mock(com.nowcoder.community.content.application.PostModerationApplicationService.class),
                mock(com.nowcoder.community.content.application.CommentApplicationService.class),
                mock(com.nowcoder.community.content.application.ModerationNoticePublisher.class),
                mock(com.nowcoder.community.user.api.action.UserModerationActionApi.class),
                mock(com.nowcoder.community.content.domain.service.ModerationDecisionDomainService.class)
        );
    }
}
