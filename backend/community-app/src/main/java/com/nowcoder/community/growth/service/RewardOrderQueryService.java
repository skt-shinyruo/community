package com.nowcoder.community.growth.service;

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
}
