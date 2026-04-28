package com.nowcoder.community.growth.domain.service;

import com.nowcoder.community.growth.domain.model.UserLevelRuleConfig;

public final class UserLevelDomainService {

    public int levelForSignInDays(int signInDays, int lv2Days, int lv3Days) {
        if (signInDays >= lv3Days) {
            return 3;
        }
        if (signInDays >= lv2Days) {
            return 2;
        }
        return 1;
    }

    public boolean isValidConfig(UserLevelRuleConfig config) {
        if (config == null || config.getWindowDays() <= 0) {
            return false;
        }
        if (config.getLv2SignInDays() <= 0 || config.getLv3SignInDays() <= 0) {
            return false;
        }
        if (config.getLv2SignInDays() >= config.getLv3SignInDays()) {
            return false;
        }
        return config.getLv3SignInDays() <= config.getWindowDays();
    }

    public void validateLevelConfig(int windowDays, int lv2Days, int lv3Days) {
        UserLevelRuleConfig config = new UserLevelRuleConfig();
        config.setWindowDays(windowDays);
        config.setLv2SignInDays(lv2Days);
        config.setLv3SignInDays(lv3Days);
        if (!isValidConfig(config)) {
            throw new IllegalArgumentException("invalid user level thresholds");
        }
    }
}
