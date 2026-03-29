package com.nowcoder.community.growth.service;

import com.nowcoder.community.growth.dto.RewardOrderResponse;
import com.nowcoder.community.growth.entity.RewardOrder;
import com.nowcoder.community.growth.mapper.RewardOrderMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RewardOrderQueryServiceTest {

    @Test
    void listOrderResponsesForUserShouldProjectRewardOrders() {
        RewardOrderMapper rewardOrderMapper = mock(RewardOrderMapper.class);
        RewardOrder order = new RewardOrder();
        order.setId(101L);
        order.setRedeemRequestId("redeem-1");
        order.setUserId(7);
        order.setItemId(11L);
        order.setStatus("FULFILLED");
        order.setCostBalanceSnapshot(12);
        order.setFulfillmentModeSnapshot("AUTO");
        order.setItemNameSnapshot("头像框周卡");
        order.setItemDescSnapshot("一周头像框权益");
        when(rewardOrderMapper.selectByUserId(7)).thenReturn(List.of(order));

        RewardOrderQueryService service = new RewardOrderQueryService(rewardOrderMapper);

        RewardOrderResponse response = service.listOrderResponsesForUser(7).get(0);

        assertThat(response.getId()).isEqualTo(101L);
        assertThat(response.getItemId()).isEqualTo(11L);
        assertThat(response.getStatus()).isEqualTo("FULFILLED");
        assertThat(response.getCostBalanceSnapshot()).isEqualTo(12);
        assertThat(response.getFulfillmentModeSnapshot()).isEqualTo("AUTO");
        assertThat(response.getItemNameSnapshot()).isEqualTo("头像框周卡");
        assertThat(response.getItemDescSnapshot()).isEqualTo("一周头像框权益");
    }
}
