package com.nowcoder.community.growth.service;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.growth.dto.UpdateUserLevelConfigRequest;
import com.nowcoder.community.growth.dto.UserLevelConfigResponse;
import com.nowcoder.community.growth.exception.GrowthErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = CommunityAppApplication.class)
@ActiveProfiles("test")
class UserLevelServiceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserLevelService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from user_level_rule_config");
        jdbcTemplate.update("delete from user_task_progress");
    }

    @Test
    void evaluateLevelShouldUseDefaultConfigAndReachLevel2AtBoundary() {
        LocalDate bizDate = LocalDate.of(2026, 4, 2);
        insertCheckIns(uuid(7), bizDate, 12);

        UserLevelService.UserLevelSummary summary = service.evaluateLevelSummary(uuid(7), bizDate);

        assertThat(summary.enabled()).isTrue();
        assertThat(summary.windowDays()).isEqualTo(100);
        assertThat(summary.lv2Threshold()).isEqualTo(12);
        assertThat(summary.lv3Threshold()).isEqualTo(88);
        assertThat(summary.signInDaysInWindow()).isEqualTo(12);
        assertThat(summary.userLevel()).isEqualTo(2);
    }

    @Test
    void evaluateLevelShouldIgnoreCheckInsOutsideRollingWindowAndReachLevel3Boundary() {
        LocalDate bizDate = LocalDate.of(2026, 4, 2);
        insertCheckIns(uuid(8), bizDate.minusDays(120), 20);
        insertCheckIns(uuid(8), bizDate, 88);

        UserLevelService.UserLevelSummary summary = service.evaluateLevelSummary(uuid(8), bizDate);

        assertThat(summary.enabled()).isTrue();
        assertThat(summary.signInDaysInWindow()).isEqualTo(88);
        assertThat(summary.userLevel()).isEqualTo(3);
    }

    @Test
    void evaluateLevelShouldReturnLevel1WhenConfigDisabled() {
        LocalDate bizDate = LocalDate.of(2026, 4, 2);
        jdbcTemplate.update(
                "insert into user_level_rule_config(id, config_key, window_days, lv2_sign_in_days, lv3_sign_in_days, enabled, updated_by, update_time) " +
                        "values (?, ?, ?, ?, ?, ?, ?, current_timestamp)",
                BinaryUuidCodec.toBytes(configId(1)), "DEFAULT", 30, 10, 20, false, BinaryUuidCodec.toBytes(uuid(1001))
        );
        insertCheckIns(uuid(9), bizDate, 30);

        UserLevelService.UserLevelSummary summary = service.evaluateLevelSummary(uuid(9), bizDate);

        assertThat(summary.enabled()).isFalse();
        assertThat(summary.windowDays()).isEqualTo(30);
        assertThat(summary.lv2Threshold()).isEqualTo(10);
        assertThat(summary.lv3Threshold()).isEqualTo(20);
        assertThat(summary.signInDaysInWindow()).isEqualTo(0);
        assertThat(summary.userLevel()).isEqualTo(1);
    }

    @Test
    void evaluateLevelShouldFallbackToDefaultWhenPersistedConfigIsInvalid() {
        LocalDate bizDate = LocalDate.of(2026, 4, 2);
        UUID userId = uuid(10);
        UUID updatedBy = uuid(1002);
        insertCheckIns(userId, bizDate, 12);
        assertFallbackToDefaultForInvalidConfig(0, 12, 88, true, updatedBy, userId, bizDate);
        assertFallbackToDefaultForInvalidConfig(100, 0, 88, true, updatedBy, userId, bizDate);
        assertFallbackToDefaultForInvalidConfig(100, 12, 0, true, updatedBy, userId, bizDate);
        assertFallbackToDefaultForInvalidConfig(100, 88, 88, true, updatedBy, userId, bizDate);
        assertFallbackToDefaultForInvalidConfig(50, 12, 88, true, updatedBy, userId, bizDate);
    }

    @Test
    void evaluateLevelShouldReadOnlySingletonCurrentConfigRow() {
        LocalDate bizDate = LocalDate.of(2026, 4, 2);
        jdbcTemplate.update(
                "insert into user_level_rule_config(id, config_key, window_days, lv2_sign_in_days, lv3_sign_in_days, enabled, updated_by, update_time) " +
                        "values (?, ?, ?, ?, ?, ?, ?, current_timestamp)",
                BinaryUuidCodec.toBytes(configId(2)), "STALE", 30, 10, 20, false, BinaryUuidCodec.toBytes(uuid(1003))
        );
        insertCheckIns(uuid(11), bizDate, 12);

        UserLevelService.UserLevelSummary summary = service.evaluateLevelSummary(uuid(11), bizDate);

        assertThat(summary.enabled()).isTrue();
        assertThat(summary.windowDays()).isEqualTo(100);
        assertThat(summary.lv2Threshold()).isEqualTo(12);
        assertThat(summary.lv3Threshold()).isEqualTo(88);
        assertThat(summary.signInDaysInWindow()).isEqualTo(12);
        assertThat(summary.userLevel()).isEqualTo(2);
    }

    @Test
    void getConfigShouldReturnDefaultWhenConfigRowDoesNotExist() {
        UserLevelConfigResponse config = service.getConfig();

        assertThat(config.getWindowDays()).isEqualTo(100);
        assertThat(config.getLv2SignInDays()).isEqualTo(12);
        assertThat(config.getLv3SignInDays()).isEqualTo(88);
        assertThat(config.isEnabled()).isTrue();
    }

    @Test
    void updateConfigShouldInsertSingletonRowOnFirstWrite() {
        UUID actorUserId = uuid(2001);
        UserLevelConfigResponse response = service.updateConfig(actorUserId, configRequest(120, 20, 90, false));

        assertThat(response.getWindowDays()).isEqualTo(120);
        assertThat(response.getLv2SignInDays()).isEqualTo(20);
        assertThat(response.getLv3SignInDays()).isEqualTo(90);
        assertThat(response.isEnabled()).isFalse();
        assertThat(jdbcTemplate.queryForObject("select count(*) from user_level_rule_config", Integer.class)).isEqualTo(1);
        UUID insertedId = BinaryUuidCodec.fromBytes(jdbcTemplate.queryForObject("select id from user_level_rule_config", byte[].class));
        assertThat(insertedId).isNotNull();
        assertThat(insertedId.version()).isEqualTo(7);
        assertThat(jdbcTemplate.queryForObject("select window_days from user_level_rule_config where config_key = 'DEFAULT'", Integer.class)).isEqualTo(120);
        assertThat(jdbcTemplate.queryForObject("select enabled from user_level_rule_config where config_key = 'DEFAULT'", Boolean.class)).isFalse();
        assertThat(BinaryUuidCodec.fromBytes(jdbcTemplate.queryForObject("select updated_by from user_level_rule_config where config_key = 'DEFAULT'", byte[].class))).isEqualTo(actorUserId);
    }

    @Test
    void updateConfigShouldUpdateExistingSingletonRowInsteadOfInsertingNewRow() {
        service.updateConfig(uuid(2001), configRequest(120, 20, 90, true));

        UUID actorUserId = uuid(2002);
        UserLevelConfigResponse response = service.updateConfig(actorUserId, configRequest(60, 10, 50, false));

        assertThat(response.getWindowDays()).isEqualTo(60);
        assertThat(response.getLv2SignInDays()).isEqualTo(10);
        assertThat(response.getLv3SignInDays()).isEqualTo(50);
        assertThat(response.isEnabled()).isFalse();
        assertThat(jdbcTemplate.queryForObject("select count(*) from user_level_rule_config", Integer.class)).isEqualTo(1);
        UUID insertedId = BinaryUuidCodec.fromBytes(jdbcTemplate.queryForObject("select id from user_level_rule_config", byte[].class));
        assertThat(insertedId).isNotNull();
        assertThat(insertedId.version()).isEqualTo(7);
        assertThat(BinaryUuidCodec.fromBytes(jdbcTemplate.queryForObject("select updated_by from user_level_rule_config where config_key = 'DEFAULT'", byte[].class))).isEqualTo(actorUserId);
    }

    @Test
    void updateConfigShouldThrowInvalidRequestWhenThresholdsAreImpossible() {
        assertThatThrownBy(() -> service.updateConfig(uuid(3001), configRequest(30, 20, 10, true)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(GrowthErrorCode.INVALID_REQUEST));
    }

    private void insertCheckIns(UUID userId, LocalDate endDateInclusive, int days) {
        for (int i = 0; i < days; i++) {
            LocalDate bizDate = endDateInclusive.minusDays(i);
            jdbcTemplate.update(
                    "insert into user_task_progress(id, user_id, task_code, period_key, current_value, target_value, status, reward_grant_id, last_source_event_id, update_time) " +
                            "values (?, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp)",
                    BinaryUuidCodec.toBytes(taskProgressId(userId, bizDate)),
                    BinaryUuidCodec.toBytes(userId),
                    "DAILY_CHECK_IN",
                    bizDate.toString(),
                    1,
                    1,
                    "CLAIMED",
                    null,
                    "check-in:" + userId + ":" + bizDate
            );
        }
    }

    private UUID taskProgressId(UUID userId, LocalDate bizDate) {
        return UUID.nameUUIDFromBytes(("user-task-progress:" + userId + ":" + bizDate).getBytes(StandardCharsets.UTF_8));
    }

    private UUID configId(int sequence) {
        return UUID.fromString("01965429-b34a-7000-8000-" + String.format("%012x", sequence));
    }

    private void assertFallbackToDefaultForInvalidConfig(
            int windowDays,
            int lv2SignInDays,
            int lv3SignInDays,
            boolean enabled,
            UUID updatedBy,
            UUID userId,
            LocalDate bizDate
    ) {
        jdbcTemplate.update("delete from user_level_rule_config");
        jdbcTemplate.update(
                "insert into user_level_rule_config(id, config_key, window_days, lv2_sign_in_days, lv3_sign_in_days, enabled, updated_by, update_time) " +
                        "values (?, ?, ?, ?, ?, ?, ?, current_timestamp)",
                BinaryUuidCodec.toBytes(configId(9)), "DEFAULT", windowDays, lv2SignInDays, lv3SignInDays, enabled, BinaryUuidCodec.toBytes(updatedBy)
        );

        UserLevelService.UserLevelSummary summary = service.evaluateLevelSummary(userId, bizDate);
        assertThat(summary.enabled()).isTrue();
        assertThat(summary.windowDays()).isEqualTo(100);
        assertThat(summary.lv2Threshold()).isEqualTo(12);
        assertThat(summary.lv3Threshold()).isEqualTo(88);
        assertThat(summary.signInDaysInWindow()).isEqualTo(12);
        assertThat(summary.userLevel()).isEqualTo(2);
    }

    private UpdateUserLevelConfigRequest configRequest(int windowDays, int lv2SignInDays, int lv3SignInDays, boolean enabled) {
        UpdateUserLevelConfigRequest request = new UpdateUserLevelConfigRequest();
        request.setWindowDays(windowDays);
        request.setLv2SignInDays(lv2SignInDays);
        request.setLv3SignInDays(lv3SignInDays);
        request.setEnabled(enabled);
        return request;
    }
}
