package com.nowcoder.community.wallet.controller;

import com.nowcoder.community.app.security.CommunitySecurityConfig;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.web.GlobalExceptionHandler;
import com.nowcoder.community.common.web.SecurityExceptionHandler;
import com.nowcoder.community.wallet.dto.CreateRechargeResponse;
import com.nowcoder.community.wallet.dto.CreateTransferResponse;
import com.nowcoder.community.wallet.dto.CreateWithdrawResponse;
import com.nowcoder.community.wallet.dto.WalletSummaryResponse;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import com.nowcoder.community.wallet.service.RechargeService;
import com.nowcoder.community.wallet.service.TransferService;
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
    private TransferService transferService;

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

        mockMvc.perform(post("/api/wallet/transfers")
                        .contentType("application/json")
                        .content("""
                                {
                                  "requestId": "transfer:req-api-1",
                                  "toUserId": 2,
                                  "amount": 300
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void walletSummaryShouldReturnCurrentBalanceForAuthenticatedUser() throws Exception {
        when(walletQueryService.summary(1)).thenReturn(new WalletSummaryResponse(1, 2300, "ACTIVE"));

        mockMvc.perform(get("/api/wallet/summary")
                        .with(jwt().jwt(jwt -> jwt.subject("1").claim("username", "u1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.balance").value(2300))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
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

    @Test
    void transferEndpointShouldReturnTransferResultForAuthenticatedUser() throws Exception {
        when(transferService.create(eq("transfer:req-api-1"), eq(1), eq(2), eq(300L)))
                .thenReturn(new CreateTransferResponse(30L, "transfer:req-api-1", 1L, 2L, 300L, "SUCCEEDED"));

        mockMvc.perform(post("/api/wallet/transfers")
                        .with(jwt().jwt(jwt -> jwt.subject("1").claim("username", "u1")))
                        .contentType("application/json")
                        .content("""
                                {
                                  "requestId": "transfer:req-api-1",
                                  "toUserId": 2,
                                  "amount": 300
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderId").value(30))
                .andExpect(jsonPath("$.data.requestId").value("transfer:req-api-1"))
                .andExpect(jsonPath("$.data.fromUserId").value(1))
                .andExpect(jsonPath("$.data.toUserId").value(2))
                .andExpect(jsonPath("$.data.amount").value(300))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
    }

    @Test
    void rechargeEndpointShouldReturnConflictForReplayPayloadMismatch() throws Exception {
        when(rechargeService.complete(eq("recharge:req-conflict"), eq(1), eq(1200L)))
                .thenThrow(new BusinessException(WalletErrorCode.REQUEST_REPLAY_CONFLICT, "requestId already used by another recharge"));

        mockMvc.perform(post("/api/wallet/recharges")
                        .with(jwt().jwt(jwt -> jwt.subject("1").claim("username", "u1")))
                        .contentType("application/json")
                        .content("""
                                {
                                  "requestId": "recharge:req-conflict",
                                  "amount": 1200
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(WalletErrorCode.REQUEST_REPLAY_CONFLICT.getCode()));
    }

    @Test
    void withdrawEndpointShouldReturnConflictForReplayPayloadMismatch() throws Exception {
        when(withdrawService.request(eq("withdraw:req-conflict"), eq(1), eq(500L)))
                .thenThrow(new BusinessException(WalletErrorCode.REQUEST_REPLAY_CONFLICT, "requestId already used by another withdraw"));

        mockMvc.perform(post("/api/wallet/withdrawals")
                        .with(jwt().jwt(jwt -> jwt.subject("1").claim("username", "u1")))
                        .contentType("application/json")
                        .content("""
                                {
                                  "requestId": "withdraw:req-conflict",
                                  "amount": 500
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(WalletErrorCode.REQUEST_REPLAY_CONFLICT.getCode()));
    }

    @Test
    void transferEndpointShouldReturnConflictForReplayPayloadMismatch() throws Exception {
        when(transferService.create(eq("transfer:req-conflict"), eq(1), eq(2), eq(300L)))
                .thenThrow(new BusinessException(WalletErrorCode.REQUEST_REPLAY_CONFLICT, "requestId already used by another transfer"));

        mockMvc.perform(post("/api/wallet/transfers")
                        .with(jwt().jwt(jwt -> jwt.subject("1").claim("username", "u1")))
                        .contentType("application/json")
                        .content("""
                                {
                                  "requestId": "transfer:req-conflict",
                                  "toUserId": 2,
                                  "amount": 300
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(WalletErrorCode.REQUEST_REPLAY_CONFLICT.getCode()));
    }
}
