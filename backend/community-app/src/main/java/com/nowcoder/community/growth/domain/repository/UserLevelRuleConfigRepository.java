package com.nowcoder.community.growth.domain.repository;

import com.nowcoder.community.growth.domain.model.UserLevelRuleConfig;

public interface UserLevelRuleConfigRepository {

    enum CreateStatus {
        CREATED,
        ALREADY_EXISTS,
        CONFLICT
    }

    record CreateResult(CreateStatus status, UserLevelRuleConfig config) {
    }

    UserLevelRuleConfig selectCurrent();

    int updateCurrent(UserLevelRuleConfig config);

    CreateResult create(UserLevelRuleConfig config);
}
