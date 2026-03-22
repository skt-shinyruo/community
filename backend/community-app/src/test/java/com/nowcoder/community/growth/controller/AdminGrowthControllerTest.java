package com.nowcoder.community.growth.controller;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.growth.dto.AdminAdjustBalanceRequest;
import com.nowcoder.community.growth.dto.AdminGrowthUserResponse;
import com.nowcoder.community.growth.service.AdminGrowthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = CommunityAppApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminGrowthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminGrowthService adminGrowthService;

    @Test
    void nonAdminRequestsShouldBeRejected() throws Exception {
        mockMvc.perform(get("/api/growth/admin/users/search")
                        .param("userId", "1")
                        .with(jwt().jwt(jwt -> jwt.subject("2")).authorities(() -> "ROLE_USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminSearchShouldReturnCurrentGrowthAccountState() throws Exception {
        AdminGrowthUserResponse response = new AdminGrowthUserResponse();
        response.setUserId(1);
        response.setUsername("u1");
        response.setScore(320);
        response.setLevel(4);
        response.setRewardBalance(15);
        response.setFrozenBalance(4);
        response.setRecentRewardLedgers(List.of());

        when(adminGrowthService.search(1, null, null)).thenReturn(response);

        mockMvc.perform(get("/api/growth/admin/users/search")
                        .param("userId", "1")
                        .with(jwt().jwt(jwt -> jwt.subject("99")).authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.score").value(320))
                .andExpect(jsonPath("$.data.level").value(4))
                .andExpect(jsonPath("$.data.rewardBalance").value(15));
    }

    @Test
    void adminAdjustShouldReturnUpdatedSnapshot() throws Exception {
        AdminGrowthUserResponse response = new AdminGrowthUserResponse();
        response.setUserId(1);
        response.setScore(0);
        response.setLevel(1);
        response.setRewardBalance(15);
        response.setFrozenBalance(0);
        response.setRecentRewardLedgers(List.of());

        when(adminGrowthService.adjust(eq(99), any(AdminAdjustBalanceRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/growth/admin/adjustments")
                        .with(jwt().jwt(jwt -> jwt.subject("99")).authorities(() -> "ROLE_ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "targetUserId": 1,
                                  "assetType": "REWARD_BALANCE",
                                  "delta": 5,
                                  "reason": "manual compensation",
                                  "confirm": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.rewardBalance").value(15));
    }
}
