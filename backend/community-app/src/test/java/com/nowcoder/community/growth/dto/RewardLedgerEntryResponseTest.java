package com.nowcoder.community.growth.dto;

import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class RewardLedgerEntryResponseTest {

    @Test
    void settersShouldPreserveFields() {
        RewardLedgerEntryResponse response = new RewardLedgerEntryResponse();
        Date createTime = new Date();

        response.setId(11L);
        response.setUserId(7);
        response.setEventId("evt-1");
        response.setEventType("TaskReward");
        response.setDelta(5);
        response.setBalanceAfter(10);
        response.setFrozenBalanceAfter(2);
        response.setBizKey("biz-1");
        response.setSourceModule("growth");
        response.setRemark("daily");
        response.setCreateTime(createTime);

        assertThat(response.getId()).isEqualTo(11L);
        assertThat(response.getUserId()).isEqualTo(7);
        assertThat(response.getEventId()).isEqualTo("evt-1");
        assertThat(response.getEventType()).isEqualTo("TaskReward");
        assertThat(response.getDelta()).isEqualTo(5);
        assertThat(response.getBalanceAfter()).isEqualTo(10);
        assertThat(response.getFrozenBalanceAfter()).isEqualTo(2);
        assertThat(response.getBizKey()).isEqualTo("biz-1");
        assertThat(response.getSourceModule()).isEqualTo("growth");
        assertThat(response.getRemark()).isEqualTo("daily");
        assertThat(response.getCreateTime()).isEqualTo(createTime);
    }
}
