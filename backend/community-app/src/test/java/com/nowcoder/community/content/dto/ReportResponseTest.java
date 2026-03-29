package com.nowcoder.community.content.dto;

import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class ReportResponseTest {

    @Test
    void settersShouldPreserveFields() {
        ReportResponse response = new ReportResponse();
        Date createTime = new Date();

        response.setId(12);
        response.setReporterId(7);
        response.setTargetType(1);
        response.setTargetId(88);
        response.setReason("spam");
        response.setDetail("details");
        response.setStatus(0);
        response.setCreateTime(createTime);

        assertThat(response.getId()).isEqualTo(12);
        assertThat(response.getReporterId()).isEqualTo(7);
        assertThat(response.getTargetType()).isEqualTo(1);
        assertThat(response.getTargetId()).isEqualTo(88);
        assertThat(response.getReason()).isEqualTo("spam");
        assertThat(response.getDetail()).isEqualTo("details");
        assertThat(response.getStatus()).isEqualTo(0);
        assertThat(response.getCreateTime()).isEqualTo(createTime);
    }
}
