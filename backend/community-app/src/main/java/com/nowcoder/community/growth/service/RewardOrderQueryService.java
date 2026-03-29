package com.nowcoder.community.growth.service;

import com.nowcoder.community.growth.dto.RewardOrderResponse;
import com.nowcoder.community.growth.entity.RewardOrder;
import com.nowcoder.community.growth.mapper.RewardOrderMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RewardOrderQueryService {

    private final RewardOrderMapper rewardOrderMapper;

    public RewardOrderQueryService(RewardOrderMapper rewardOrderMapper) {
        this.rewardOrderMapper = rewardOrderMapper;
    }

    public List<RewardOrder> listOrdersForUser(int userId) {
        return rewardOrderMapper.selectByUserId(userId);
    }

    public List<RewardOrderResponse> listOrderResponsesForUser(int userId) {
        return listOrdersForUser(userId).stream().map(this::toOrderResponse).toList();
    }

    private RewardOrderResponse toOrderResponse(RewardOrder order) {
        RewardOrderResponse response = new RewardOrderResponse();
        response.setId(order.getId());
        response.setItemId(order.getItemId());
        response.setStatus(order.getStatus());
        response.setCostBalanceSnapshot(order.getCostBalanceSnapshot());
        response.setFulfillmentModeSnapshot(order.getFulfillmentModeSnapshot());
        response.setItemNameSnapshot(order.getItemNameSnapshot());
        response.setItemDescSnapshot(order.getItemDescSnapshot());
        return response;
    }
}
