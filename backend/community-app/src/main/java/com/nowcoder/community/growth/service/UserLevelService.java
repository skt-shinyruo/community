package com.nowcoder.community.growth.service;

import com.nowcoder.community.growth.entity.UserLevelRuleConfig;
import com.nowcoder.community.growth.mapper.GrowthCheckInMapper;
import com.nowcoder.community.growth.mapper.UserLevelRuleConfigMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class UserLevelService {

    public static final int DEFAULT_WINDOW_DAYS = 100;
    public static final int DEFAULT_LV2_SIGN_IN_DAYS = 12;
    public static final int DEFAULT_LV3_SIGN_IN_DAYS = 88;

    private final GrowthCheckInMapper growthCheckInMapper;
    private final UserLevelRuleConfigMapper userLevelRuleConfigMapper;

    public UserLevelService(GrowthCheckInMapper growthCheckInMapper, UserLevelRuleConfigMapper userLevelRuleConfigMapper) {
        this.growthCheckInMapper = growthCheckInMapper;
        this.userLevelRuleConfigMapper = userLevelRuleConfigMapper;
    }

    public UserLevelSummary evaluateLevel(int userId, LocalDate bizDate) {
        UserLevelRuleConfig config = activeConfigOrDefault();
        if (!config.isEnabled()) {
            return new UserLevelSummary(
                    1,
                    0,
                    config.getWindowDays(),
                    config.getLv2SignInDays(),
                    config.getLv3SignInDays(),
                    false
            );
        }

        LocalDate startDate = bizDate.minusDays(config.getWindowDays() - 1L);
        int signInDaysInWindow = growthCheckInMapper.countByUserIdBetweenDates(userId, startDate, bizDate);
        int userLevel = 1;
        if (signInDaysInWindow >= config.getLv3SignInDays()) {
            userLevel = 3;
        } else if (signInDaysInWindow >= config.getLv2SignInDays()) {
            userLevel = 2;
        }

        return new UserLevelSummary(
                userLevel,
                signInDaysInWindow,
                config.getWindowDays(),
                config.getLv2SignInDays(),
                config.getLv3SignInDays(),
                true
        );
    }

    public UserLevelRuleConfig activeConfigOrDefault() {
        UserLevelRuleConfig config = userLevelRuleConfigMapper.selectLatest();
        return config == null ? defaultConfig() : config;
    }

    private UserLevelRuleConfig defaultConfig() {
        UserLevelRuleConfig config = new UserLevelRuleConfig();
        config.setWindowDays(DEFAULT_WINDOW_DAYS);
        config.setLv2SignInDays(DEFAULT_LV2_SIGN_IN_DAYS);
        config.setLv3SignInDays(DEFAULT_LV3_SIGN_IN_DAYS);
        config.setEnabled(true);
        return config;
    }

    public record UserLevelSummary(
            int userLevel,
            int signInDaysInWindow,
            int windowDays,
            int lv2Threshold,
            int lv3Threshold,
            boolean enabled
    ) {
    }
}
