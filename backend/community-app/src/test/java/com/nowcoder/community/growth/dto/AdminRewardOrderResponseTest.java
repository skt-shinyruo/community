package com.nowcoder.community.growth.dto;

import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class AdminRewardOrderResponseTest {

    @Test
    void settersShouldPreserveFields() {
        AdminRewardOrderResponse response = new AdminRewardOrderResponse();
        Date createTime = new Date();
        Date updateTime = new Date(createTime.getTime() + 1_000);

        response.setId(101L);
        response.setRedeemRequestId("redeem-1");
        response.setUserId(7);
        response.setItemId(11L);
        response.setStatus("PENDING");
        response.setCostBalanceSnapshot(15);
        response.setFulfillmentModeSnapshot("MANUAL");
        response.setItemNameSnapshot("社群资格");
        response.setItemDescSnapshot("人工发放");
        response.setCreateTime(createTime);
        response.setUpdateTime(updateTime);

        assertThat(response.getId()).isEqualTo(101L);
        assertThat(response.getRedeemRequestId()).isEqualTo("redeem-1");
        assertThat(response.getUserId()).isEqualTo(7);
        assertThat(response.getItemId()).isEqualTo(11L);
        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getCostBalanceSnapshot()).isEqualTo(15);
        assertThat(response.getFulfillmentModeSnapshot()).isEqualTo("MANUAL");
        assertThat(response.getItemNameSnapshot()).isEqualTo("社群资格");
        assertThat(response.getItemDescSnapshot()).isEqualTo("人工发放");
        assertThat(response.getCreateTime()).isEqualTo(createTime);
        assertThat(response.getUpdateTime()).isEqualTo(updateTime);
    }
}
