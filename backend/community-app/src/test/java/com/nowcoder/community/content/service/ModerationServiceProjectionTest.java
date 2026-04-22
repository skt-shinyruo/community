package com.nowcoder.community.content.service;

import com.nowcoder.community.content.dto.ModerationActionResponse;
import com.nowcoder.community.content.dto.ReportResponse;
import com.nowcoder.community.content.entity.ModerationAction;
import com.nowcoder.community.content.entity.Report;
import com.nowcoder.community.content.mapper.ModerationActionMapper;
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
    void listReportResponsesShouldProjectReports() {
        ReportService reportService = mock(ReportService.class);
        ModerationActionMapper actionMapper = mock(ModerationActionMapper.class);
        Report report = new Report();
        report.setId(REPORT_ID);
        report.setReporterId(REPORTER_ID);
        report.setTargetType(1);
        report.setTargetId(TARGET_ID);
        report.setReason("spam");
        report.setDetail("details");
        report.setStatus(0);
        report.setCreateTime(new Date());
        when(reportService.listReports(0, 1, REPORTER_ID, 0, 20)).thenReturn(List.of(report));

        ModerationService service = new ModerationService(reportService, actionMapper);

        ReportResponse response = service.listReportResponses(0, 1, REPORTER_ID, 0, 20).get(0);

        assertThat(response.getId()).isEqualTo(REPORT_ID);
        assertThat(response.getReporterId()).isEqualTo(REPORTER_ID);
        assertThat(response.getTargetType()).isEqualTo(1);
        assertThat(response.getTargetId()).isEqualTo(TARGET_ID);
        assertThat(response.getReason()).isEqualTo("spam");
        assertThat(response.getDetail()).isEqualTo("details");
        assertThat(response.getStatus()).isEqualTo(0);
    }

    @Test
    void listModerationActionResponsesShouldProjectActions() {
        ReportService reportService = mock(ReportService.class);
        ModerationActionMapper actionMapper = mock(ModerationActionMapper.class);
        ModerationAction action = new ModerationAction();
        action.setId(ACTION_ID);
        action.setReportId(REPORT_ID);
        action.setActorId(ACTOR_ID);
        action.setAction("ban");
        action.setReason("abuse");
        action.setDurationSeconds(3600);
        action.setCreateTime(new Date());
        when(actionMapper.selectActions(ACTOR_ID, 0, 20)).thenReturn(List.of(action));

        ModerationService service = new ModerationService(reportService, actionMapper);

        ModerationActionResponse response = service.listModerationActionResponses(ACTOR_ID, 0, 20).get(0);

        assertThat(response.getId()).isEqualTo(ACTION_ID);
        assertThat(response.getReportId()).isEqualTo(REPORT_ID);
        assertThat(response.getActorId()).isEqualTo(ACTOR_ID);
        assertThat(response.getAction()).isEqualTo("ban");
        assertThat(response.getReason()).isEqualTo("abuse");
        assertThat(response.getDurationSeconds()).isEqualTo(3600);
    }
}
