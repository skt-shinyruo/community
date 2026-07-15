package com.nowcoder.community.growth.infrastructure.persistence;

import com.nowcoder.community.growth.domain.model.UserLevelRuleConfig;
import com.nowcoder.community.growth.domain.repository.UserLevelRuleConfigRepository;
import com.nowcoder.community.growth.infrastructure.persistence.dataobject.UserLevelRuleConfigDataObject;
import com.nowcoder.community.growth.infrastructure.persistence.mapper.UserLevelRuleConfigMapper;
import org.springframework.dao.DuplicateKeyException;
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
    public CreateResult create(UserLevelRuleConfig config) {
        try {
            return userLevelRuleConfigMapper.insert(UserLevelRuleConfigDataObject.from(config)) == 1
                    ? new CreateResult(CreateStatus.CREATED, config)
                    : new CreateResult(CreateStatus.CONFLICT, null);
        } catch (DuplicateKeyException ignored) {
            UserLevelRuleConfig existing = selectCurrent();
            return existing == null
                    ? new CreateResult(CreateStatus.CONFLICT, null)
                    : new CreateResult(CreateStatus.ALREADY_EXISTS, existing);
        }
    }
}
