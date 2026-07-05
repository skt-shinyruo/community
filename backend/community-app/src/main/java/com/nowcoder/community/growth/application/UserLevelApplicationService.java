package com.nowcoder.community.growth.application;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.growth.application.command.UpdateUserLevelConfigCommand;
import com.nowcoder.community.growth.application.result.UserLevelConfigResult;
import com.nowcoder.community.growth.application.result.UserLevelSummaryResult;
import com.nowcoder.community.growth.domain.model.UserLevelRuleConfig;
import com.nowcoder.community.growth.domain.repository.UserLevelRuleConfigRepository;
import com.nowcoder.community.growth.domain.repository.UserTaskProgressRepository;
import com.nowcoder.community.growth.domain.service.UserLevelDomainService;
import com.nowcoder.community.growth.exception.GrowthErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Service
public class UserLevelApplicationService {

    private static final String DAILY_CHECK_IN_TASK_CODE = "DAILY_CHECK_IN";
    public static final int DEFAULT_WINDOW_DAYS = 100;
    public static final int DEFAULT_LV2_SIGN_IN_DAYS = 12;
    public static final int DEFAULT_LV3_SIGN_IN_DAYS = 88;

    private final UserTaskProgressRepository userTaskProgressRepository;
    private final UserLevelRuleConfigRepository userLevelRuleConfigRepository;
    private final GrowthBusinessTimeService growthBusinessTimeService;
    private final UserLevelDomainService userLevelDomainService;
    private final UuidV7Generator idGenerator;

    @Autowired
    public UserLevelApplicationService(
            UserTaskProgressRepository userTaskProgressRepository,
            UserLevelRuleConfigRepository userLevelRuleConfigRepository,
            GrowthBusinessTimeService growthBusinessTimeService
    ) {
        this(userTaskProgressRepository, userLevelRuleConfigRepository, growthBusinessTimeService, new UserLevelDomainService(), new UuidV7Generator());
    }

    UserLevelApplicationService(
            UserTaskProgressRepository userTaskProgressRepository,
            UserLevelRuleConfigRepository userLevelRuleConfigRepository,
            GrowthBusinessTimeService growthBusinessTimeService,
            UserLevelDomainService userLevelDomainService,
            UuidV7Generator idGenerator
    ) {
        this.userTaskProgressRepository = userTaskProgressRepository;
        this.userLevelRuleConfigRepository = userLevelRuleConfigRepository;
        this.growthBusinessTimeService = growthBusinessTimeService;
        this.userLevelDomainService = userLevelDomainService;
        this.idGenerator = idGenerator;
    }

    public UserLevelSummaryResult evaluateLevel(UUID userId) {
        return evaluateLevelSummary(userId, growthBusinessTimeService.today());
    }

    public UserLevelSummaryResult evaluateLevel(UUID userId, LocalDate bizDate) {
        return evaluateLevelSummary(userId, bizDate);
    }

    public UserLevelSummaryResult evaluateLevelSummary(UUID userId, LocalDate bizDate) {
        UserLevelRuleConfig config = activeConfigOrDefault();
        if (!config.isEnabled()) {
            return new UserLevelSummaryResult(
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

        return new UserLevelSummaryResult(
                userLevel,
                signInDaysInWindow,
                config.getWindowDays(),
                config.getLv2SignInDays(),
                config.getLv3SignInDays(),
                true
        );
    }

    public UserLevelConfigResult getConfig() {
        return toConfigResponse(activeConfigOrDefault());
    }

    @Transactional
    public UserLevelConfigResult updateConfig(UpdateUserLevelConfigCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return updateConfigInternal(command);
    }

    @Transactional
    public UserLevelConfigResult updateConfig(UUID actorUserId, UpdateUserLevelConfigCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        command.setActorUserId(actorUserId);
        return updateConfigInternal(command);
    }

    private UserLevelConfigResult updateConfigInternal(UpdateUserLevelConfigCommand command) {
        validateUpdateRequest(command);

        UserLevelRuleConfig config = new UserLevelRuleConfig();
        config.setWindowDays(command.getWindowDays());
        config.setLv2SignInDays(command.getLv2SignInDays());
        config.setLv3SignInDays(command.getLv3SignInDays());
        config.setEnabled(Boolean.TRUE.equals(command.getEnabled()));
        config.setUpdatedBy(command.getActorUserId());

        int updated = userLevelRuleConfigRepository.updateCurrent(config);
        if (updated <= 0) {
            config.setId(idGenerator.next());
            try {
                userLevelRuleConfigRepository.insert(config);
            } catch (DuplicateKeyException ex) {
                userLevelRuleConfigRepository.updateCurrent(config);
            }
        }
        return toConfigResponse(config);
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

    private void validateUpdateRequest(UpdateUserLevelConfigCommand command) {
        if (command.getEnabled() == null) {
            throw new BusinessException(GrowthErrorCode.INVALID_REQUEST, "enabled required");
        }

        try {
            userLevelDomainService.validateLevelConfig(
                    command.getWindowDays(),
                    command.getLv2SignInDays(),
                    command.getLv3SignInDays()
            );
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(GrowthErrorCode.INVALID_REQUEST, "invalid user level thresholds");
        }
    }

    private UserLevelConfigResult toConfigResponse(UserLevelRuleConfig config) {
        UserLevelConfigResult response = new UserLevelConfigResult();
        response.setWindowDays(config.getWindowDays());
        response.setLv2SignInDays(config.getLv2SignInDays());
        response.setLv3SignInDays(config.getLv3SignInDays());
        response.setEnabled(config.isEnabled());
        return response;
    }

}
