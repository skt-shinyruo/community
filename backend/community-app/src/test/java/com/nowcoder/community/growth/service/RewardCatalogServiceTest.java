package com.nowcoder.community.growth.service;

import com.nowcoder.community.growth.dto.RewardItemResponse;
import com.nowcoder.community.growth.entity.RewardItem;
import com.nowcoder.community.growth.mapper.RewardItemMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RewardCatalogServiceTest {

    @Test
    void listItemResponsesForUserShouldProjectRewardItems() {
        RewardItemMapper rewardItemMapper = mock(RewardItemMapper.class);
        RewardItem item = new RewardItem();
        item.setId(11L);
        item.setItemName("头像框周卡");
        item.setItemDesc("一周头像框权益");
        item.setCostBalance(12);
        item.setStock(5);
        item.setPerUserLimit(1);
        item.setFulfillmentMode("AUTO");
        item.setStatus("ACTIVE");
        when(rewardItemMapper.selectActiveOrdered()).thenReturn(List.of(item));

        RewardCatalogService service = new RewardCatalogService(rewardItemMapper);

        RewardItemResponse response = service.listItemResponsesForUser(1).get(0);

        assertThat(response.getId()).isEqualTo(11L);
        assertThat(response.getItemName()).isEqualTo("头像框周卡");
        assertThat(response.getItemDesc()).isEqualTo("一周头像框权益");
        assertThat(response.getCostBalance()).isEqualTo(12);
        assertThat(response.getStock()).isEqualTo(5);
        assertThat(response.getPerUserLimit()).isEqualTo(1);
        assertThat(response.getFulfillmentMode()).isEqualTo("AUTO");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
    }
}
