package com.nowcoder.community.growth.service;

import com.nowcoder.community.app.CommunityAppApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

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
        jdbcTemplate.update("delete from growth_check_in");
    }

    @Test
    void evaluateLevelShouldUseDefaultConfigAndReachLevel2AtBoundary() {
        LocalDate bizDate = LocalDate.of(2026, 4, 2);
        insertCheckIns(7, bizDate, 12);

        UserLevelService.UserLevelSummary summary = service.evaluateLevel(7, bizDate);

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
        insertCheckIns(8, bizDate.minusDays(120), 20);
        insertCheckIns(8, bizDate, 88);

        UserLevelService.UserLevelSummary summary = service.evaluateLevel(8, bizDate);

        assertThat(summary.enabled()).isTrue();
        assertThat(summary.signInDaysInWindow()).isEqualTo(88);
        assertThat(summary.userLevel()).isEqualTo(3);
    }

    @Test
    void evaluateLevelShouldReturnLevel1WhenConfigDisabled() {
        LocalDate bizDate = LocalDate.of(2026, 4, 2);
        jdbcTemplate.update(
                "insert into user_level_rule_config(window_days, lv2_sign_in_days, lv3_sign_in_days, enabled, updated_by, update_time) " +
                        "values (?, ?, ?, ?, ?, current_timestamp)",
                30, 10, 20, false, 1001
        );
        insertCheckIns(9, bizDate, 30);

        UserLevelService.UserLevelSummary summary = service.evaluateLevel(9, bizDate);

        assertThat(summary.enabled()).isFalse();
        assertThat(summary.windowDays()).isEqualTo(30);
        assertThat(summary.lv2Threshold()).isEqualTo(10);
        assertThat(summary.lv3Threshold()).isEqualTo(20);
        assertThat(summary.signInDaysInWindow()).isEqualTo(0);
        assertThat(summary.userLevel()).isEqualTo(1);
    }

    private void insertCheckIns(int userId, LocalDate endDateInclusive, int days) {
        for (int i = 0; i < days; i++) {
            LocalDate bizDate = endDateInclusive.minusDays(i);
            jdbcTemplate.update(
                    "insert into growth_check_in(user_id, biz_date, streak_count, create_time) values (?, ?, ?, current_timestamp)",
                    userId,
                    bizDate,
                    1
            );
        }
    }
}
