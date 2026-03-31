package com.nowcoder.community.growth.service;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class CheckInServiceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CheckInService service;

    @MockBean
    private UnifiedGrantService unifiedGrantService;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from growth_check_in");
        when(unifiedGrantService.applyGrant(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(true);
    }

    @Test
    void sameUserShouldOnlyCheckInOncePerBusinessDate() {
        LocalDate today = LocalDate.of(2026, 3, 22);

        CheckInService.CheckInResult first = service.checkIn(1, today);
        CheckInService.CheckInResult second = service.checkIn(1, today);

        assertThat(first.newlyCheckedIn()).isTrue();
        assertThat(second.newlyCheckedIn()).isFalse();
        assertThat(checkInRowCount(1)).isEqualTo(1);
        verify(unifiedGrantService, times(1)).applyGrant(
                eq(1),
                eq("check-in:1:2026-03-22"),
                eq("DailyCheckIn"),
                eq("check-in:1:2026-03-22"),
                eq("DailyCheckIn"),
                eq(5),
                eq(2),
                eq("growth"),
                eq("daily-check-in")
        );
    }

    @Test
    void consecutiveBusinessDatesShouldIncreaseCurrentStreak() {
        service.checkIn(1, LocalDate.of(2026, 3, 20));
        CheckInService.CheckInResult next = service.checkIn(1, LocalDate.of(2026, 3, 21));

        assertThat(next.currentStreak()).isEqualTo(2);
        assertThat(service.status(1, LocalDate.of(2026, 3, 21)).currentStreak()).isEqualTo(2);
        assertThat(service.status(1, LocalDate.of(2026, 3, 21)).maxStreak()).isEqualTo(2);
        assertThat(service.status(1, LocalDate.of(2026, 3, 21)).totalCheckInDays()).isEqualTo(2);
    }

    @Test
    void gapShouldResetCurrentStreakButKeepMaxStreakAndHistory() {
        service.checkIn(1, LocalDate.of(2026, 3, 20));
        service.checkIn(1, LocalDate.of(2026, 3, 21));

        CheckInService.CheckInResult result = service.checkIn(1, LocalDate.of(2026, 3, 23));

        assertThat(result.currentStreak()).isEqualTo(1);
        CheckInService.CheckInStatus status = service.status(1, LocalDate.of(2026, 3, 23));
        assertThat(status.currentStreak()).isEqualTo(1);
        assertThat(status.maxStreak()).isEqualTo(2);
        assertThat(status.totalCheckInDays()).isEqualTo(3);
    }

    private int checkInRowCount(int userId) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from growth_check_in where user_id = ?", Integer.class, userId);
        return count == null ? 0 : count;
    }
}
