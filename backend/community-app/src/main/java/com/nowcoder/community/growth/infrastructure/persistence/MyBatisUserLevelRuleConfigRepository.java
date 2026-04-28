package com.nowcoder.community.growth.infrastructure.persistence;

import com.nowcoder.community.growth.domain.model.UserLevelRuleConfig;
import com.nowcoder.community.growth.domain.repository.UserLevelRuleConfigRepository;
import com.nowcoder.community.growth.infrastructure.persistence.dataobject.UserLevelRuleConfigDataObject;
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

    @Override
    public int updateCurrent(UserLevelRuleConfig config) {
        return userLevelRuleConfigMapper.updateCurrent(UserLevelRuleConfigDataObject.from(config));
    }

    @Override
    public int insert(UserLevelRuleConfig config) {
        return userLevelRuleConfigMapper.insert(UserLevelRuleConfigDataObject.from(config));
    }
}
