package com.nowcoder.community.growth.service;

import com.nowcoder.community.growth.api.model.UserLevelSummaryView;
import com.nowcoder.community.growth.api.query.UserLevelQueryApi;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.growth.dto.UpdateUserLevelConfigRequest;
import com.nowcoder.community.growth.dto.UserLevelConfigResponse;
import com.nowcoder.community.growth.entity.UserLevelRuleConfig;
import com.nowcoder.community.growth.exception.GrowthErrorCode;
import com.nowcoder.community.growth.mapper.UserTaskProgressMapper;
import com.nowcoder.community.growth.mapper.UserLevelRuleConfigMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class UserLevelService implements UserLevelQueryApi {

    private static final String DAILY_CHECK_IN_TASK_CODE = "DAILY_CHECK_IN";
    public static final int DEFAULT_WINDOW_DAYS = 100;
    public static final int DEFAULT_LV2_SIGN_IN_DAYS = 12;
    public static final int DEFAULT_LV3_SIGN_IN_DAYS = 88;
    private static final long SINGLETON_CONFIG_ID = 1L;

    private final UserTaskProgressMapper userTaskProgressMapper;
    private final UserLevelRuleConfigMapper userLevelRuleConfigMapper;
    private final GrowthBusinessTimeService growthBusinessTimeService;

    public UserLevelService(
            UserTaskProgressMapper userTaskProgressMapper,
            UserLevelRuleConfigMapper userLevelRuleConfigMapper,
            GrowthBusinessTimeService growthBusinessTimeService
    ) {
        this.userTaskProgressMapper = userTaskProgressMapper;
        this.userLevelRuleConfigMapper = userLevelRuleConfigMapper;
        this.growthBusinessTimeService = growthBusinessTimeService;
    }

    @Override
    public UserLevelSummaryView evaluateLevel(int userId) {
        return toView(evaluateLevelSummary(userId, growthBusinessTimeService.today()));
    }

    public UserLevelSummaryView evaluateLevel(int userId, LocalDate bizDate) {
        return toView(evaluateLevelSummary(userId, bizDate));
    }

    public UserLevelSummary evaluateLevelSummary(int userId, LocalDate bizDate) {
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
        int signInDaysInWindow = userTaskProgressMapper.countByUserTaskAndPeriodKeyRange(
                userId,
                DAILY_CHECK_IN_TASK_CODE,
                startDate.toString(),
                bizDate.toString()
        );
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

    public UserLevelConfigResponse getConfig() {
        return toConfigResponse(activeConfigOrDefault());
    }

    @Transactional
    public UserLevelConfigResponse updateConfig(int actorUserId, UpdateUserLevelConfigRequest request) {
        validateUpdateRequest(request);

        UserLevelRuleConfig config = new UserLevelRuleConfig();
        config.setId(SINGLETON_CONFIG_ID);
        config.setWindowDays(request.getWindowDays());
        config.setLv2SignInDays(request.getLv2SignInDays());
        config.setLv3SignInDays(request.getLv3SignInDays());
        config.setEnabled(Boolean.TRUE.equals(request.getEnabled()));
        config.setUpdatedBy(actorUserId);

        int updated = userLevelRuleConfigMapper.updateCurrent(config);
        if (updated <= 0) {
            try {
                userLevelRuleConfigMapper.insert(config);
            } catch (DuplicateKeyException ex) {
                userLevelRuleConfigMapper.updateCurrent(config);
            }
        }
        return toConfigResponse(config);
    }

    public UserLevelRuleConfig activeConfigOrDefault() {
        UserLevelRuleConfig config = userLevelRuleConfigMapper.selectCurrent();
        if (config == null || !isValidConfig(config)) {
            return defaultConfig();
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

    private boolean isValidConfig(UserLevelRuleConfig config) {
        if (config.getWindowDays() <= 0) {
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

    private void validateUpdateRequest(UpdateUserLevelConfigRequest request) {
        if (request == null) {
            throw new BusinessException(GrowthErrorCode.INVALID_REQUEST, "config request required");
        }
        if (request.getEnabled() == null) {
            throw new BusinessException(GrowthErrorCode.INVALID_REQUEST, "enabled required");
        }

        UserLevelRuleConfig config = new UserLevelRuleConfig();
        config.setWindowDays(request.getWindowDays());
        config.setLv2SignInDays(request.getLv2SignInDays());
        config.setLv3SignInDays(request.getLv3SignInDays());
        if (!isValidConfig(config)) {
            throw new BusinessException(GrowthErrorCode.INVALID_REQUEST, "invalid user level thresholds");
        }
    }

    private UserLevelConfigResponse toConfigResponse(UserLevelRuleConfig config) {
        UserLevelConfigResponse response = new UserLevelConfigResponse();
        response.setWindowDays(config.getWindowDays());
        response.setLv2SignInDays(config.getLv2SignInDays());
        response.setLv3SignInDays(config.getLv3SignInDays());
        response.setEnabled(config.isEnabled());
        return response;
    }

    private UserLevelSummaryView toView(UserLevelSummary summary) {
        return new UserLevelSummaryView(
                summary.userLevel(),
                summary.signInDaysInWindow(),
                summary.windowDays(),
                summary.lv2Threshold(),
                summary.lv3Threshold(),
                summary.enabled()
        );
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
