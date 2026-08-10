package com.nowcoder.community.growth.application;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.growth.api.model.UserLevelSummaryView;
import com.nowcoder.community.growth.api.query.UserLevelQueryApi;
import com.nowcoder.community.growth.domain.model.UserLevelRuleConfig;
import com.nowcoder.community.growth.domain.repository.UserLevelRuleConfigRepository;
import com.nowcoder.community.growth.domain.repository.UserTaskProgressRepository;
import com.nowcoder.community.growth.domain.service.UserLevelDomainService;
import com.nowcoder.community.growth.exception.GrowthErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INTERNAL_ERROR;

@Service
public class UserLevelApplicationService implements UserLevelQueryApi {

    public record UpdateConfigCommand(
            UUID actorUserId,
            int windowDays,
            int lv2SignInDays,
            int lv3SignInDays,
            Boolean enabled
    ) {
    }

    public record UserLevelConfigResult(
            int windowDays,
            int lv2SignInDays,
            int lv3SignInDays,
            boolean enabled
    ) {
        // Keep the existing bean-style accessors for callers that consume the application result directly.
        public int getWindowDays() {
            return windowDays;
        }

        public int getLv2SignInDays() {
            return lv2SignInDays;
        }

        public int getLv3SignInDays() {
            return lv3SignInDays;
        }

        public boolean isEnabled() {
            return enabled;
        }
    }

    private static final String DAILY_CHECK_IN_TASK_CODE = "DAILY_CHECK_IN";
    public static final int DEFAULT_WINDOW_DAYS = 100;
    public static final int DEFAULT_LV2_SIGN_IN_DAYS = 12;
    public static final int DEFAULT_LV3_SIGN_IN_DAYS = 88;

    private final UserTaskProgressRepository userTaskProgressRepository;
    private final UserLevelRuleConfigRepository userLevelRuleConfigRepository;
    private final GrowthBusinessTimeService growthBusinessTimeService;
    private final UserLevelDomainService userLevelDomainService = new UserLevelDomainService();
    private final UuidV7Generator idGenerator;

    @Autowired
    public UserLevelApplicationService(
            UserTaskProgressRepository userTaskProgressRepository,
            UserLevelRuleConfigRepository userLevelRuleConfigRepository,
            GrowthBusinessTimeService growthBusinessTimeService,
            UuidV7Generator idGenerator
    ) {
        this.userTaskProgressRepository = Objects.requireNonNull(userTaskProgressRepository, "userTaskProgressRepository must not be null");
        this.userLevelRuleConfigRepository = Objects.requireNonNull(userLevelRuleConfigRepository, "userLevelRuleConfigRepository must not be null");
        this.growthBusinessTimeService = Objects.requireNonNull(growthBusinessTimeService, "growthBusinessTimeService must not be null");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator must not be null");
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

    public UserLevelConfigResult getConfig() {
        return toConfigResponse(activeConfigOrDefault());
    }

    @Transactional
    public UserLevelConfigResult updateConfig(UpdateConfigCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return updateConfigInternal(command);
    }

    @Transactional
    public UserLevelConfigResult updateConfig(UUID actorUserId, UpdateConfigCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return updateConfigInternal(new UpdateConfigCommand(
                actorUserId,
                command.windowDays(),
                command.lv2SignInDays(),
                command.lv3SignInDays(),
                command.enabled()
        ));
    }

    private UserLevelConfigResult updateConfigInternal(UpdateConfigCommand command) {
        validateUpdateRequest(command);

        UserLevelRuleConfig config = new UserLevelRuleConfig();
        config.setWindowDays(command.windowDays());
        config.setLv2SignInDays(command.lv2SignInDays());
        config.setLv3SignInDays(command.lv3SignInDays());
        config.setEnabled(Boolean.TRUE.equals(command.enabled()));
        config.setUpdatedBy(command.actorUserId());

        int updated = userLevelRuleConfigRepository.updateCurrent(config);
        if (updated <= 0) {
            config.setId(idGenerator.next());
            UserLevelRuleConfigRepository.CreateResult result = userLevelRuleConfigRepository.create(config);
            if (result == null || result.status() == UserLevelRuleConfigRepository.CreateStatus.CONFLICT) {
                throw new BusinessException(INTERNAL_ERROR, "用户等级配置创建失败");
            }
            if (result.status() == UserLevelRuleConfigRepository.CreateStatus.ALREADY_EXISTS
                    && userLevelRuleConfigRepository.updateCurrent(config) <= 0) {
                throw new BusinessException(INTERNAL_ERROR, "用户等级配置更新失败");
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

    private void validateUpdateRequest(UpdateConfigCommand command) {
        if (command.enabled() == null) {
            throw new BusinessException(GrowthErrorCode.INVALID_REQUEST, "enabled required");
        }

        try {
            userLevelDomainService.validateLevelConfig(
                    command.windowDays(),
                    command.lv2SignInDays(),
                    command.lv3SignInDays()
            );
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(GrowthErrorCode.INVALID_REQUEST, "invalid user level thresholds");
        }
    }

    private UserLevelConfigResult toConfigResponse(UserLevelRuleConfig config) {
        return new UserLevelConfigResult(
                config.getWindowDays(),
                config.getLv2SignInDays(),
                config.getLv3SignInDays(),
                config.isEnabled()
        );
    }

}
