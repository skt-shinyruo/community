package com.nowcoder.community.growth.infrastructure.persistence.dataobject;

import com.nowcoder.community.growth.domain.model.UserLevelRuleConfig;

public class UserLevelRuleConfigDataObject extends UserLevelRuleConfig {

    public static UserLevelRuleConfigDataObject from(UserLevelRuleConfig config) {
        UserLevelRuleConfigDataObject row = new UserLevelRuleConfigDataObject();
        row.setId(config.getId());
        row.setWindowDays(config.getWindowDays());
        row.setLv2SignInDays(config.getLv2SignInDays());
        row.setLv3SignInDays(config.getLv3SignInDays());
        row.setEnabled(config.isEnabled());
        row.setUpdatedBy(config.getUpdatedBy());
        row.setUpdateTime(config.getUpdateTime());
        return row;
    }
}
