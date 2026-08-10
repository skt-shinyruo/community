package com.nowcoder.community.content.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.content.domain.model.Report;
import com.nowcoder.community.content.domain.repository.CommentRepository;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.domain.repository.ReportContentRepository;
import com.nowcoder.community.user.api.model.UserSummaryView;
import com.nowcoder.community.user.api.query.UserLookupQueryApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2025-01-02T03:04:05Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private ReportContentRepository reportContentRepository;
    private PostContentRepository postContentRepository;
    private CommentRepository commentRepository;
    private UserLookupQueryApi userLookupQueryApi;
    private ReportApplicationService service;

    @BeforeEach
    void setUp() {
        reportContentRepository = mock(ReportContentRepository.class);
        postContentRepository = mock(PostContentRepository.class);
        commentRepository = mock(CommentRepository.class);
        userLookupQueryApi = mock(UserLookupQueryApi.class);
        service = new ReportApplicationService(
                reportContentRepository,
                postContentRepository,
                commentRepository,
                userLookupQueryApi,
                CLOCK
        );
    }

    @Test
    void createShouldValidateAndPersistReportThroughRepository() {
        UUID reporterId = uuid(1);
        UUID targetId = uuid(2);
        UUID reportId = uuid(3);
        when(reportContentRepository.createReport(any(Report.class))).thenReturn(reportId);

        UUID createdId = service.create(reporterId, "post", targetId, " spam ", " detail ");

        assertThat(createdId).isEqualTo(reportId);
        verify(postContentRepository).getById(targetId);
        ArgumentCaptor<Report> reportCaptor = ArgumentCaptor.forClass(Report.class);
        verify(reportContentRepository).createReport(reportCaptor.capture());
        Report report = reportCaptor.getValue();
        assertThat(report.getReporterId()).isEqualTo(reporterId);
        assertThat(report.getTargetType()).isEqualTo(ReportContentRepository.TARGET_TYPE_POST);
        assertThat(report.getTargetId()).isEqualTo(targetId);
        assertThat(report.getReason()).isEqualTo("spam");
        assertThat(report.getDetail()).isEqualTo("detail");
        assertThat(report.getStatus()).isEqualTo(ReportContentRepository.STATUS_PENDING);
        assertThat(report.getCreateTime()).isEqualTo(Date.from(NOW));
    }

    @Test
    void createShouldRejectMissingActiveCommentTarget() {
        UUID reporterId = uuid(1);
        UUID targetId = uuid(2);
        when(commentRepository.findActiveSnapshot(targetId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(reporterId, "comment", targetId, "spam", ""))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @Test
    void createShouldReturnExistingReportIdWhenRepositoryWriteConflicts() {
        UUID reporterId = uuid(1);
        UUID targetId = uuid(2);
        UUID existingReportId = uuid(3);
        when(userLookupQueryApi.getSummaryById(targetId))
                .thenReturn(new UserSummaryView(targetId, "target", "", 0));
        when(reportContentRepository.createReport(any(Report.class))).thenThrow(new RuntimeException("duplicate"));
        when(reportContentRepository.findExistingReportId(eq(reporterId), eq(ReportContentRepository.TARGET_TYPE_USER), eq(targetId)))
                .thenReturn(existingReportId);

        UUID createdId = service.create(reporterId, "user", targetId, "spam", "");

        assertThat(createdId).isEqualTo(existingReportId);
    }

    @Test
    void createShouldRejectMissingUserTarget() {
        UUID reporterId = uuid(1);
        UUID targetId = uuid(2);
        when(userLookupQueryApi.getSummaryById(targetId)).thenReturn(null);

        assertThatThrownBy(() -> service.create(reporterId, "user", targetId, "spam", ""))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }
}
