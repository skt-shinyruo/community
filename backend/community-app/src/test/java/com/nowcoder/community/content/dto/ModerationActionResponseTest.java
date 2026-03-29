package com.nowcoder.community.content.dto;

import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class ModerationActionResponseTest {

    @Test
    void settersShouldPreserveFields() {
        ModerationActionResponse response = new ModerationActionResponse();
        Date createTime = new Date();

        response.setId(21);
        response.setReportId(12);
        response.setActorId(99);
        response.setAction("ban");
        response.setReason("abuse");
        response.setDurationSeconds(3600);
        response.setCreateTime(createTime);

        assertThat(response.getId()).isEqualTo(21);
        assertThat(response.getReportId()).isEqualTo(12);
        assertThat(response.getActorId()).isEqualTo(99);
        assertThat(response.getAction()).isEqualTo("ban");
        assertThat(response.getReason()).isEqualTo("abuse");
        assertThat(response.getDurationSeconds()).isEqualTo(3600);
        assertThat(response.getCreateTime()).isEqualTo(createTime);
    }
}
