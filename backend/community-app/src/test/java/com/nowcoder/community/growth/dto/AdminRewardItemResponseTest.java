package com.nowcoder.community.growth.dto;

import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class AdminRewardItemResponseTest {

    @Test
    void settersShouldPreserveFields() {
        AdminRewardItemResponse response = new AdminRewardItemResponse();
        Date createTime = new Date();
        Date updateTime = new Date(createTime.getTime() + 1_000);

        response.setId(11L);
        response.setItemName("社群资格");
        response.setItemDesc("人工发放");
        response.setCostBalance(15);
        response.setStock(5);
        response.setPerUserLimit(1);
        response.setFulfillmentMode("MANUAL");
        response.setStatus("ACTIVE");
        response.setCreateTime(createTime);
        response.setUpdateTime(updateTime);

        assertThat(response.getId()).isEqualTo(11L);
        assertThat(response.getItemName()).isEqualTo("社群资格");
        assertThat(response.getItemDesc()).isEqualTo("人工发放");
        assertThat(response.getCostBalance()).isEqualTo(15);
        assertThat(response.getStock()).isEqualTo(5);
        assertThat(response.getPerUserLimit()).isEqualTo(1);
        assertThat(response.getFulfillmentMode()).isEqualTo("MANUAL");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.getCreateTime()).isEqualTo(createTime);
        assertThat(response.getUpdateTime()).isEqualTo(updateTime);
    }
}
