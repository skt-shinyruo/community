package com.nowcoder.community.growth.controller;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.growth.dto.AdminAdjustBalanceRequest;
import com.nowcoder.community.growth.dto.AdminRewardAdjustmentResponse;
import com.nowcoder.community.growth.dto.AdminGrowthUserResponse;
import com.nowcoder.community.growth.dto.RewardLedgerEntryResponse;
import com.nowcoder.community.growth.dto.UpdateUserLevelConfigRequest;
import com.nowcoder.community.growth.dto.UserLevelConfigResponse;
import com.nowcoder.community.growth.exception.GrowthErrorCode;
import com.nowcoder.community.growth.service.AdminGrowthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
        response.setUserLevel(2);
        response.setSignInDaysInWindow(13);
        response.setWindowDays(100);
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
                .andExpect(jsonPath("$.data.userLevel").value(2))
                .andExpect(jsonPath("$.data.signInDaysInWindow").value(13))
                .andExpect(jsonPath("$.data.windowDays").value(100))
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

    @Test
    void ledgersShouldReturnDtoResponses() throws Exception {
        RewardLedgerEntryResponse ledger = new RewardLedgerEntryResponse();
        ledger.setId(11L);
        ledger.setUserId(1);
        ledger.setEventType("TaskReward");
        ledger.setDelta(5);
        ledger.setBalanceAfter(10);
        ledger.setCreateTime(new Date());
        when(adminGrowthService.recentRewardLedgerResponses(1, 10)).thenReturn(List.of(ledger));

        mockMvc.perform(get("/api/growth/admin/users/1/ledgers")
                        .with(jwt().jwt(jwt -> jwt.subject("99")).authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(11))
                .andExpect(jsonPath("$.data[0].eventType").value("TaskReward"))
                .andExpect(jsonPath("$.data[0].delta").value(5))
                .andExpect(jsonPath("$.data[0].balanceAfter").value(10));
    }

    @Test
    void adjustmentsShouldReturnDtoResponses() throws Exception {
        AdminRewardAdjustmentResponse adjustment = new AdminRewardAdjustmentResponse();
        adjustment.setId(21L);
        adjustment.setActorUserId(99);
        adjustment.setTargetUserId(1);
        adjustment.setAssetType("REWARD_BALANCE");
        adjustment.setDelta(5);
        adjustment.setBeforeValue(10);
        adjustment.setAfterValue(15);
        adjustment.setReason("manual compensation");
        adjustment.setConfirmToken("confirmed");
        adjustment.setCreateTime(new Date());
        when(adminGrowthService.recentAdjustmentResponses(1, 10)).thenReturn(List.of(adjustment));

        mockMvc.perform(get("/api/growth/admin/users/1/adjustments")
                        .with(jwt().jwt(jwt -> jwt.subject("99")).authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(21))
                .andExpect(jsonPath("$.data[0].assetType").value("REWARD_BALANCE"))
                .andExpect(jsonPath("$.data[0].delta").value(5))
                .andExpect(jsonPath("$.data[0].beforeValue").value(10))
                .andExpect(jsonPath("$.data[0].afterValue").value(15))
                .andExpect(jsonPath("$.data[0].confirmToken").value("confirmed"));
    }

    @Test
    void getUserLevelConfigShouldReturnConfigForAdmin() throws Exception {
        UserLevelConfigResponse response = new UserLevelConfigResponse();
        response.setWindowDays(100);
        response.setLv2SignInDays(12);
        response.setLv3SignInDays(88);
        response.setEnabled(true);
        when(adminGrowthService.getUserLevelConfig()).thenReturn(response);

        mockMvc.perform(get("/api/growth/admin/user-level/config")
                        .with(jwt().jwt(jwt -> jwt.subject("99")).authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.windowDays").value(100))
                .andExpect(jsonPath("$.data.lv2SignInDays").value(12))
                .andExpect(jsonPath("$.data.lv3SignInDays").value(88))
                .andExpect(jsonPath("$.data.enabled").value(true));
    }

    @Test
    void nonAdminGetUserLevelConfigShouldBeRejected() throws Exception {
        mockMvc.perform(get("/api/growth/admin/user-level/config")
                        .with(jwt().jwt(jwt -> jwt.subject("2")).authorities(() -> "ROLE_USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateUserLevelConfigShouldPersistForAdmin() throws Exception {
        UserLevelConfigResponse response = new UserLevelConfigResponse();
        response.setWindowDays(120);
        response.setLv2SignInDays(20);
        response.setLv3SignInDays(90);
        response.setEnabled(true);
        when(adminGrowthService.updateUserLevelConfig(eq(99), any(UpdateUserLevelConfigRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/growth/admin/user-level/config")
                        .with(jwt().jwt(jwt -> jwt.subject("99")).authorities(() -> "ROLE_ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "windowDays": 120,
                                  "lv2SignInDays": 20,
                                  "lv3SignInDays": 90,
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.windowDays").value(120))
                .andExpect(jsonPath("$.data.lv2SignInDays").value(20))
                .andExpect(jsonPath("$.data.lv3SignInDays").value(90))
                .andExpect(jsonPath("$.data.enabled").value(true));
    }

    @Test
    void updateUserLevelConfigShouldUseGrowthInvalidRequestForInvalidPayload() throws Exception {
        when(adminGrowthService.updateUserLevelConfig(eq(99), any(UpdateUserLevelConfigRequest.class)))
                .thenThrow(new BusinessException(GrowthErrorCode.INVALID_REQUEST, "invalid user level thresholds"));

        mockMvc.perform(put("/api/growth/admin/user-level/config")
                        .with(jwt().jwt(jwt -> jwt.subject("99")).authorities(() -> "ROLE_ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "windowDays": 0,
                                  "lv2SignInDays": 20,
                                  "lv3SignInDays": 90,
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(16001));
    }
}
