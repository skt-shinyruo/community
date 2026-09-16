package com.nowcoder.community.growth.application;

import com.nowcoder.community.growth.api.model.UserLevelSummaryView;
import com.nowcoder.community.growth.api.query.UserLevelQueryApi;
import com.nowcoder.community.growth.domain.model.UserLevelRuleConfig;
import com.nowcoder.community.growth.domain.repository.UserLevelRuleConfigRepository;
import com.nowcoder.community.growth.domain.repository.UserTaskProgressRepository;
import com.nowcoder.community.growth.domain.service.UserLevelDomainService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Service
public class UserLevelApplicationService implements UserLevelQueryApi {

    private static final String DAILY_CHECK_IN_TASK_CODE = "DAILY_CHECK_IN";
    public static final int DEFAULT_WINDOW_DAYS = 100;
    public static final int DEFAULT_LV2_SIGN_IN_DAYS = 12;
    public static final int DEFAULT_LV3_SIGN_IN_DAYS = 88;

    private final UserTaskProgressRepository userTaskProgressRepository;
    private final UserLevelRuleConfigRepository userLevelRuleConfigRepository;
    private final GrowthBusinessTimeService growthBusinessTimeService;
    private final UserLevelDomainService userLevelDomainService = new UserLevelDomainService();

    @Autowired
    public UserLevelApplicationService(
            UserTaskProgressRepository userTaskProgressRepository,
            UserLevelRuleConfigRepository userLevelRuleConfigRepository,
            GrowthBusinessTimeService growthBusinessTimeService
    ) {
        this.userTaskProgressRepository = Objects.requireNonNull(userTaskProgressRepository, "userTaskProgressRepository must not be null");
        this.userLevelRuleConfigRepository = Objects.requireNonNull(userLevelRuleConfigRepository, "userLevelRuleConfigRepository must not be null");
        this.growthBusinessTimeService = Objects.requireNonNull(growthBusinessTimeService, "growthBusinessTimeService must not be null");
    }

    @Override
    public UserLevelSummaryView evaluateLevel(UUID userId) {
        return evaluateLevelSummary(userId, growthBusinessTimeService.today());
    }

    public UserLevelSummaryView evaluateLevel(UUID userId, LocalDate bizDate) {
        return evaluateLevelSummary(userId, bizDate);
    }

    public UserLevelSummaryView evaluateLevelSummary(UUID userId, LocalDate bizDate) {
        UserLevelRuleConfig config = activeConfigOrDefault();
        if (!config.isEnabled()) {
            return new UserLevelSummaryView(
                    1,
                    0,
                    config.getWindowDays(),
                    config.getLv2SignInDays(),
                    config.getLv3SignInDays(),
                    false
            );
        }

        LocalDate startDate = bizDate.minusDays(config.getWindowDays() - 1L);
        int signInDaysInWindow = userTaskProgressRepository.countCompletedByUserTaskAndPeriodKeyRange(
                userId,
                DAILY_CHECK_IN_TASK_CODE,
                startDate.toString(),
                bizDate.toString()
        );
        int userLevel = userLevelDomainService.levelForSignInDays(
                signInDaysInWindow,
                config.getLv2SignInDays(),
                config.getLv3SignInDays()
        );

        return new UserLevelSummaryView(
                userLevel,
                signInDaysInWindow,
                config.getWindowDays(),
                config.getLv2SignInDays(),
                config.getLv3SignInDays(),
                true
        );
    }

    public UserLevelRuleConfig activeConfigOrDefault() {
        UserLevelRuleConfig config = userLevelRuleConfigRepository.selectCurrent();
        if (config == null) {
            return defaultConfig();
        }
        if (!userLevelDomainService.isValidConfig(config)) {
            throw new IllegalStateException("invalid user level rule config");
        }
        return config;
    }

    private UserLevelRuleConfig defaultConfig() {
        UserLevelRuleConfig config = new UserLevelRuleConfig();
        config.setWindowDays(DEFAULT_WINDOW_DAYS);
        config.setLv2SignInDays(DEFAULT_LV2_SIGN_IN_DAYS);
        config.setLv3SignInDays(DEFAULT_LV3_SIGN_IN_DAYS);
        config.setEnabled(true);
        return config;
    }
}
