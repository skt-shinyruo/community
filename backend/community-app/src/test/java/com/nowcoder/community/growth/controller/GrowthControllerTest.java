package com.nowcoder.community.growth.controller;

import com.nowcoder.community.growth.security.GrowthSecurityRules;
import com.nowcoder.community.growth.dto.TaskCenterResponse;
import com.nowcoder.community.growth.dto.TaskItemResponse;
import com.nowcoder.community.growth.service.GrowthBusinessTimeService;
import com.nowcoder.community.growth.service.RewardAccountService;
import com.nowcoder.community.growth.service.TaskCenterService;
import com.nowcoder.community.infra.web.GlobalExceptionHandler;
import com.nowcoder.community.infra.web.SecurityExceptionHandler;
import com.nowcoder.community.user.api.model.UserGrowthProfileView;
import com.nowcoder.community.user.api.query.UserProfileQueryApi;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GrowthController.class)
@Import({
        GrowthController.class,
        com.nowcoder.community.app.security.CommunitySecurityConfig.class,
        GrowthSecurityRules.class,
        SecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class GrowthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserProfileQueryApi userProfileQueryApi;

    @MockBean
    private RewardAccountService rewardAccountService;

    @MockBean
    private TaskCenterService taskCenterService;

    @MockBean
    private GrowthBusinessTimeService growthBusinessTimeService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @Test
    void growthSummaryShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/growth/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("未登录或登录已失效"));
    }

    @Test
    void growthSummaryShouldReturnScoreLevelAndRewardBalancesForAuthenticatedUser() throws Exception {
        when(userProfileQueryApi.getGrowthProfile(1))
                .thenReturn(new UserGrowthProfileView(1, "u1", 320, 4, "u1@example.com", 1, "h1"));
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
        when(userProfileQueryApi.getGrowthProfile(2))
                .thenReturn(new UserGrowthProfileView(2, "u2", 0, 1, "u2@example.com", 1, "h2"));
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
        LocalDate bizDate = LocalDate.of(2026, 3, 22);
        TaskCenterResponse response = new TaskCenterResponse();
        response.setBizDate(bizDate);
        response.setItems(java.util.List.of(
                task("DAILY_CHECK_IN", "DAILY", "2026-03-22", 0, 1, "IN_PROGRESS"),
                task("DAILY_POST", "DAILY", "2026-03-22", 0, 1, "IN_PROGRESS"),
                task("WEEKLY_COMMENTER", "WEEKLY", "2026-W12", 0, 3, "IN_PROGRESS"),
                task("LIFETIME_RECEIVE_LIKE", "LIFETIME", "LIFETIME", 0, 10, "IN_PROGRESS")
        ));
        when(growthBusinessTimeService.today()).thenReturn(bizDate);
        when(taskCenterService.snapshot(1, bizDate)).thenReturn(response);

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

    private TaskItemResponse task(
            String taskCode,
            String periodType,
            String periodKey,
            int currentValue,
            int targetValue,
            String status
    ) {
        TaskItemResponse item = new TaskItemResponse();
        item.setTaskCode(taskCode);
        item.setPeriodType(periodType);
        item.setPeriodKey(periodKey);
        item.setCurrentValue(currentValue);
        item.setTargetValue(targetValue);
        item.setStatus(status);
        return item;
    }
}
