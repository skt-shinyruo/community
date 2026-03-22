package com.nowcoder.community.growth.controller;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.growth.service.GrowthBusinessTimeService;
import com.nowcoder.community.growth.service.RewardAccountService;
import com.nowcoder.community.user.entity.User;
import com.nowcoder.community.user.service.PointsService;
import com.nowcoder.community.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = CommunityAppApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GrowthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private UserService userService;

    @MockBean
    private PointsService pointsService;

    @MockBean
    private RewardAccountService rewardAccountService;

    @MockBean
    private GrowthBusinessTimeService growthBusinessTimeService;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from user_task_progress");
    }

    @Test
    void growthSummaryShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/growth/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void growthSummaryShouldReturnScoreLevelAndRewardBalancesForAuthenticatedUser() throws Exception {
        User user = new User();
        user.setId(1);
        user.setUsername("u1");
        user.setScore(320);

        when(userService.getById(1)).thenReturn(user);
        when(pointsService.levelForScore(320)).thenReturn(4);
        when(rewardAccountService.availableBalanceOf(1)).thenReturn(55);
        when(rewardAccountService.frozenBalanceOf(1)).thenReturn(7);

        mockMvc.perform(get("/api/growth/summary")
                        .with(jwt().jwt(jwt -> jwt.subject("1").claim("username", "u1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.score").value(320))
                .andExpect(jsonPath("$.data.level").value(4))
                .andExpect(jsonPath("$.data.rewardBalance").value(55))
                .andExpect(jsonPath("$.data.frozenBalance").value(7));
    }

    @Test
    void growthSummaryShouldTreatMissingRewardAccountAsZeroBalanceView() throws Exception {
        User user = new User();
        user.setId(2);
        user.setUsername("u2");
        user.setScore(0);

        when(userService.getById(2)).thenReturn(user);
        when(pointsService.levelForScore(0)).thenReturn(1);
        when(rewardAccountService.availableBalanceOf(2)).thenReturn(0);
        when(rewardAccountService.frozenBalanceOf(2)).thenReturn(0);

        mockMvc.perform(get("/api/growth/summary")
                        .with(jwt().jwt(jwt -> jwt.subject("2").claim("username", "u2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value(2))
                .andExpect(jsonPath("$.data.rewardBalance").value(0))
                .andExpect(jsonPath("$.data.frozenBalance").value(0));
    }

    @Test
    void growthTasksShouldUseBusinessDateWhenClientDoesNotPassOne() throws Exception {
        when(growthBusinessTimeService.today()).thenReturn(java.time.LocalDate.of(2026, 3, 22));

        mockMvc.perform(get("/api/growth/tasks")
                        .with(jwt().jwt(jwt -> jwt.subject("1").claim("username", "u1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.bizDate").value("2026-03-22"))
                .andExpect(jsonPath("$.data.items.length()").value(4))
                .andExpect(jsonPath("$.data.items[0].taskCode").value("DAILY_CHECK_IN"))
                .andExpect(jsonPath("$.data.items[0].periodType").value("DAILY"))
                .andExpect(jsonPath("$.data.items[0].currentValue").value(0))
                .andExpect(jsonPath("$.data.items[0].targetValue").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.items[1].taskCode").value("DAILY_POST"))
                .andExpect(jsonPath("$.data.items[2].taskCode").value("WEEKLY_COMMENTER"))
                .andExpect(jsonPath("$.data.items[2].periodKey").value("2026-W12"))
                .andExpect(jsonPath("$.data.items[3].taskCode").value("LIFETIME_RECEIVE_LIKE"))
                .andExpect(jsonPath("$.data.items[3].periodKey").value("LIFETIME"));
    }
}
