package com.nowcoder.community.growth.infrastructure.persistence;

import com.nowcoder.community.growth.domain.model.UserLevelRuleConfig;
import com.nowcoder.community.growth.domain.repository.UserLevelRuleConfigRepository;
import com.nowcoder.community.growth.infrastructure.persistence.mapper.UserLevelRuleConfigMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisUserLevelRuleConfigRepository implements UserLevelRuleConfigRepository {

    private final UserLevelRuleConfigMapper userLevelRuleConfigMapper;

    public MyBatisUserLevelRuleConfigRepository(UserLevelRuleConfigMapper userLevelRuleConfigMapper) {
        this.userLevelRuleConfigMapper = userLevelRuleConfigMapper;
    }

    @Override
    public UserLevelRuleConfig selectCurrent() {
        return userLevelRuleConfigMapper.selectCurrent();
    }
}
