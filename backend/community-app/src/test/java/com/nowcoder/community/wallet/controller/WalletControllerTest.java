package com.nowcoder.community.wallet.controller;

import com.nowcoder.community.app.security.CommunitySecurityConfig;
import com.nowcoder.community.common.web.GlobalExceptionHandler;
import com.nowcoder.community.common.web.SecurityExceptionHandler;
import com.nowcoder.community.wallet.dto.CreateRechargeResponse;
import com.nowcoder.community.wallet.dto.CreateWithdrawResponse;
import com.nowcoder.community.wallet.dto.WalletSummaryResponse;
import com.nowcoder.community.wallet.service.RechargeService;
import com.nowcoder.community.wallet.service.WalletQueryService;
import com.nowcoder.community.wallet.service.WithdrawService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WalletController.class)
@Import({
        WalletController.class,
        CommunitySecurityConfig.class,
        SecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class WalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WalletQueryService walletQueryService;

    @MockBean
    private RechargeService rechargeService;

    @MockBean
    private WithdrawService withdrawService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @Test
    void walletApisShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/wallet/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(post("/api/wallet/recharges")
                        .contentType("application/json")
                        .content("""
                                {
                                  "requestId": "recharge:req-api-1",
                                  "amount": 1200
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(post("/api/wallet/withdrawals")
                        .contentType("application/json")
                        .content("""
                                {
                                  "requestId": "withdraw:req-api-1",
                                  "amount": 500
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void walletSummaryShouldReturnCurrentBalanceForAuthenticatedUser() throws Exception {
        when(walletQueryService.summary(1)).thenReturn(new WalletSummaryResponse(1, 2300));

        mockMvc.perform(get("/api/wallet/summary")
                        .with(jwt().jwt(jwt -> jwt.subject("1").claim("username", "u1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.balance").value(2300));
    }

    @Test
    void rechargeEndpointShouldReturnRechargeResultForAuthenticatedUser() throws Exception {
        when(rechargeService.complete(eq("recharge:req-api-1"), eq(1), eq(1200L)))
                .thenReturn(new CreateRechargeResponse(10L, "recharge:req-api-1", 1L, 1200L, "PAID"));

        mockMvc.perform(post("/api/wallet/recharges")
                        .with(jwt().jwt(jwt -> jwt.subject("1").claim("username", "u1")))
                        .contentType("application/json")
                        .content("""
                                {
                                  "requestId": "recharge:req-api-1",
                                  "amount": 1200
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderId").value(10))
                .andExpect(jsonPath("$.data.requestId").value("recharge:req-api-1"))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.amount").value(1200))
                .andExpect(jsonPath("$.data.status").value("PAID"));
    }

    @Test
    void withdrawEndpointShouldReturnWithdrawResultForAuthenticatedUser() throws Exception {
        when(withdrawService.request(eq("withdraw:req-api-1"), eq(1), eq(500L)))
                .thenReturn(new CreateWithdrawResponse(20L, "withdraw:req-api-1", 1L, 500L, "SUCCEEDED"));

        mockMvc.perform(post("/api/wallet/withdrawals")
                        .with(jwt().jwt(jwt -> jwt.subject("1").claim("username", "u1")))
                        .contentType("application/json")
                        .content("""
                                {
                                  "requestId": "withdraw:req-api-1",
                                  "amount": 500
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderId").value(20))
                .andExpect(jsonPath("$.data.requestId").value("withdraw:req-api-1"))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.amount").value(500))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
    }
}
