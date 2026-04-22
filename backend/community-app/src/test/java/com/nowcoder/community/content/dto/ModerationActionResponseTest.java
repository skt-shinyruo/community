package com.nowcoder.community.content.dto;

import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;

class ModerationActionResponseTest {

    private static final UUID ACTION_ID = UUID.fromString("00000000-0000-7000-8000-000000000309");
    private static final UUID REPORT_ID = UUID.fromString("00000000-0000-7000-8000-00000000030a");
    private static final UUID ACTOR_ID = uuid(99);

    @Test
    void settersShouldPreserveFields() {
        ModerationActionResponse response = new ModerationActionResponse();
        Date createTime = new Date();

        response.setId(ACTION_ID);
        response.setReportId(REPORT_ID);
        response.setActorId(ACTOR_ID);
        response.setAction("ban");
        response.setReason("abuse");
        response.setDurationSeconds(3600);
        response.setCreateTime(createTime);

        assertThat(response.getId()).isEqualTo(ACTION_ID);
        assertThat(response.getReportId()).isEqualTo(REPORT_ID);
        assertThat(response.getActorId()).isEqualTo(ACTOR_ID);
        assertThat(response.getAction()).isEqualTo("ban");
        assertThat(response.getReason()).isEqualTo("abuse");
        assertThat(response.getDurationSeconds()).isEqualTo(3600);
        assertThat(response.getCreateTime()).isEqualTo(createTime);
    }
}
