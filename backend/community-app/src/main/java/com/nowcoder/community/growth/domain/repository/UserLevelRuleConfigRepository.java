package com.nowcoder.community.growth.domain.repository;

import com.nowcoder.community.growth.domain.model.UserLevelRuleConfig;

public interface UserLevelRuleConfigRepository {

    UserLevelRuleConfig selectCurrent();
}
