package com.nowcoder.community.growth.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.growth.dto.RewardItemResponse;
import com.nowcoder.community.growth.entity.RewardItem;
import com.nowcoder.community.growth.exception.GrowthErrorCode;
import com.nowcoder.community.growth.mapper.RewardItemMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RewardCatalogService {

    private final RewardItemMapper rewardItemMapper;

    public RewardCatalogService(RewardItemMapper rewardItemMapper) {
        this.rewardItemMapper = rewardItemMapper;
    }

    public List<RewardItem> listItemsForUser(int userId) {
        return rewardItemMapper.selectActiveOrdered();
    }

    public List<RewardItemResponse> listItemResponsesForUser(int userId) {
        return listItemsForUser(userId).stream().map(this::toItemResponse).toList();
    }

    public RewardItem getItemForUser(int userId, long itemId) {
        RewardItem item = rewardItemMapper.selectById(itemId);
        if (item == null || !"ACTIVE".equals(item.getStatus())) {
            throw new BusinessException(GrowthErrorCode.REWARD_ITEM_UNAVAILABLE, "reward item unavailable: itemId=" + itemId);
        }
        return item;
    }

    public RewardItemResponse getItemResponseForUser(int userId, long itemId) {
        return toItemResponse(getItemForUser(userId, itemId));
    }

    private RewardItemResponse toItemResponse(RewardItem item) {
        RewardItemResponse response = new RewardItemResponse();
        response.setId(item.getId());
        response.setItemName(item.getItemName());
        response.setItemDesc(item.getItemDesc());
        response.setCostBalance(item.getCostBalance());
        response.setStock(item.getStock());
        response.setPerUserLimit(item.getPerUserLimit());
        response.setFulfillmentMode(item.getFulfillmentMode());
        response.setStatus(item.getStatus());
        return response;
    }
}
