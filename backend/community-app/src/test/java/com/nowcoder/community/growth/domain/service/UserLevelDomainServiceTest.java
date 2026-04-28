package com.nowcoder.community.growth.domain.service;

import com.nowcoder.community.growth.domain.model.UserLevelRuleConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserLevelDomainServiceTest {

    private final UserLevelDomainService service = new UserLevelDomainService();

    @Test
    void levelForSignInDaysShouldUseConfiguredThresholds() {
        assertThat(service.levelForSignInDays(0, 12, 88)).isEqualTo(1);
        assertThat(service.levelForSignInDays(12, 12, 88)).isEqualTo(2);
        assertThat(service.levelForSignInDays(88, 12, 88)).isEqualTo(3);
    }

    @Test
    void invalidConfigShouldBeRejected() {
        assertThat(service.isValidConfig(config(100, 12, 88))).isTrue();
        assertThat(service.isValidConfig(config(0, 12, 88))).isFalse();
        assertThat(service.isValidConfig(config(100, 88, 88))).isFalse();
        assertThat(service.isValidConfig(config(50, 12, 88))).isFalse();
        assertThatThrownBy(() -> service.validateLevelConfig(30, 20, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private UserLevelRuleConfig config(int windowDays, int lv2Days, int lv3Days) {
        UserLevelRuleConfig config = new UserLevelRuleConfig();
        config.setWindowDays(windowDays);
        config.setLv2SignInDays(lv2Days);
        config.setLv3SignInDays(lv3Days);
        config.setEnabled(true);
        return config;
    }
}
