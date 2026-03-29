package com.nowcoder.community.growth.dto;

import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class AdminRewardAdjustmentResponseTest {

    @Test
    void settersShouldPreserveFields() {
        AdminRewardAdjustmentResponse response = new AdminRewardAdjustmentResponse();
        Date createTime = new Date();

        response.setId(21L);
        response.setActorUserId(99);
        response.setTargetUserId(7);
        response.setAssetType("REWARD_BALANCE");
        response.setDelta(5);
        response.setBeforeValue(10);
        response.setAfterValue(15);
        response.setReason("manual compensation");
        response.setConfirmToken("confirmed");
        response.setCreateTime(createTime);

        assertThat(response.getId()).isEqualTo(21L);
        assertThat(response.getActorUserId()).isEqualTo(99);
        assertThat(response.getTargetUserId()).isEqualTo(7);
        assertThat(response.getAssetType()).isEqualTo("REWARD_BALANCE");
        assertThat(response.getDelta()).isEqualTo(5);
        assertThat(response.getBeforeValue()).isEqualTo(10);
        assertThat(response.getAfterValue()).isEqualTo(15);
        assertThat(response.getReason()).isEqualTo("manual compensation");
        assertThat(response.getConfirmToken()).isEqualTo("confirmed");
        assertThat(response.getCreateTime()).isEqualTo(createTime);
    }
}
