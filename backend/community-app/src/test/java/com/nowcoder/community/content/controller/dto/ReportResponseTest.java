package com.nowcoder.community.content.controller.dto;

import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;

class ReportResponseTest {

    private static final UUID REPORT_ID = UUID.fromString("00000000-0000-7000-8000-000000000308");

    @Test
    void settersShouldPreserveFields() {
        ReportResponse response = new ReportResponse();
        Date createTime = new Date();
        UUID reporterId = uuid(7);
        UUID targetId = uuid(88);

        response.setId(REPORT_ID);
        response.setReporterId(reporterId);
        response.setTargetType(1);
        response.setTargetId(targetId);
        response.setReason("spam");
        response.setDetail("details");
        response.setStatus(0);
        response.setCreateTime(createTime);

        assertThat(response.getId()).isEqualTo(REPORT_ID);
        assertThat(response.getReporterId()).isEqualTo(reporterId);
        assertThat(response.getTargetType()).isEqualTo(1);
        assertThat(response.getTargetId()).isEqualTo(targetId);
        assertThat(response.getReason()).isEqualTo("spam");
        assertThat(response.getDetail()).isEqualTo("details");
        assertThat(response.getStatus()).isEqualTo(0);
        assertThat(response.getCreateTime()).isEqualTo(createTime);
    }
}
