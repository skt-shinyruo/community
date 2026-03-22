package com.nowcoder.community.growth.service;

import com.nowcoder.community.common.exception.BusinessException;
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

    public RewardItem getItemForUser(int userId, long itemId) {
        RewardItem item = rewardItemMapper.selectById(itemId);
        if (item == null || !"ACTIVE".equals(item.getStatus())) {
            throw new BusinessException(GrowthErrorCode.REWARD_ITEM_UNAVAILABLE, "reward item unavailable: itemId=" + itemId);
        }
        return item;
    }
}
