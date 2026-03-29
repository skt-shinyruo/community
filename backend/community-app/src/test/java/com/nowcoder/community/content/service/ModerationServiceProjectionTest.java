package com.nowcoder.community.content.service;

import com.nowcoder.community.content.dto.ModerationActionResponse;
import com.nowcoder.community.content.dto.ReportResponse;
import com.nowcoder.community.content.entity.ModerationAction;
import com.nowcoder.community.content.entity.Report;
import com.nowcoder.community.content.mapper.CommentMapper;
import com.nowcoder.community.content.mapper.DiscussPostMapper;
import com.nowcoder.community.content.mapper.ModerationActionMapper;
import com.nowcoder.community.content.event.ContentEventPublisher;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModerationServiceProjectionTest {

    @Test
    void listReportResponsesShouldProjectReports() {
        ReportService reportService = mock(ReportService.class);
        ModerationActionMapper actionMapper = mock(ModerationActionMapper.class);
        DiscussPostMapper discussPostMapper = mock(DiscussPostMapper.class);
        CommentMapper commentMapper = mock(CommentMapper.class);
        ContentEventPublisher eventPublisher = mock(ContentEventPublisher.class);
        Report report = new Report();
        report.setId(12);
        report.setReporterId(7);
        report.setTargetType(1);
        report.setTargetId(88);
        report.setReason("spam");
        report.setDetail("details");
        report.setStatus(0);
        report.setCreateTime(new Date());
        when(reportService.listReports(0, 1, 7, 0, 20)).thenReturn(List.of(report));

        ModerationService service = new ModerationService(reportService, actionMapper, discussPostMapper, commentMapper, eventPublisher);

        ReportResponse response = service.listReportResponses(0, 1, 7, 0, 20).get(0);

        assertThat(response.getId()).isEqualTo(12);
        assertThat(response.getReporterId()).isEqualTo(7);
        assertThat(response.getTargetType()).isEqualTo(1);
        assertThat(response.getTargetId()).isEqualTo(88);
        assertThat(response.getReason()).isEqualTo("spam");
        assertThat(response.getDetail()).isEqualTo("details");
        assertThat(response.getStatus()).isEqualTo(0);
    }

    @Test
    void listModerationActionResponsesShouldProjectActions() {
        ReportService reportService = mock(ReportService.class);
        ModerationActionMapper actionMapper = mock(ModerationActionMapper.class);
        DiscussPostMapper discussPostMapper = mock(DiscussPostMapper.class);
        CommentMapper commentMapper = mock(CommentMapper.class);
        ContentEventPublisher eventPublisher = mock(ContentEventPublisher.class);
        ModerationAction action = new ModerationAction();
        action.setId(21);
        action.setReportId(12);
        action.setActorId(99);
        action.setAction("ban");
        action.setReason("abuse");
        action.setDurationSeconds(3600);
        action.setCreateTime(new Date());
        when(actionMapper.selectActions(99, 0, 20)).thenReturn(List.of(action));

        ModerationService service = new ModerationService(reportService, actionMapper, discussPostMapper, commentMapper, eventPublisher);

        ModerationActionResponse response = service.listModerationActionResponses(99, 0, 20).get(0);

        assertThat(response.getId()).isEqualTo(21);
        assertThat(response.getReportId()).isEqualTo(12);
        assertThat(response.getActorId()).isEqualTo(99);
        assertThat(response.getAction()).isEqualTo("ban");
        assertThat(response.getReason()).isEqualTo("abuse");
        assertThat(response.getDurationSeconds()).isEqualTo(3600);
    }
}
