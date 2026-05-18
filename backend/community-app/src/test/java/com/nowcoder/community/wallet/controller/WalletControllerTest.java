package com.nowcoder.community.wallet.controller;

import com.nowcoder.community.app.security.CommunitySecurityConfig;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.common.web.GlobalExceptionHandler;
import com.nowcoder.community.common.web.SecurityExceptionHandler;
import com.nowcoder.community.wallet.application.WalletAccountApplicationService;
import com.nowcoder.community.wallet.application.WalletApplicationService;
import com.nowcoder.community.wallet.application.WalletLedgerApplicationService;
import com.nowcoder.community.wallet.application.WalletRechargeApplicationService;
import com.nowcoder.community.wallet.application.WalletTransferApplicationService;
import com.nowcoder.community.wallet.application.WalletWithdrawApplicationService;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import com.nowcoder.community.wallet.application.result.RechargeOrderResult;
import com.nowcoder.community.wallet.application.result.TransferOrderResult;
import com.nowcoder.community.wallet.application.result.WalletTransactionResult;
import com.nowcoder.community.wallet.application.result.WithdrawOrderResult;
import com.nowcoder.community.wallet.domain.model.WalletAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WalletController.class)
@Import({
        WalletController.class,
        WalletApplicationService.class,
        CommunitySecurityConfig.class,
        SecurityExceptionHandler.class,
        GlobalExceptionHandler.class
})
class WalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WalletAccountApplicationService accountService;

    @MockBean
    private WalletRechargeApplicationService rechargeService;

    @MockBean
    private WalletWithdrawApplicationService withdrawService;

    @MockBean
    private WalletTransferApplicationService transferService;

    @MockBean
    private WalletLedgerApplicationService ledgerService;

    @MockBean
    private IdempotencyGuard idempotencyGuard;

    @MockBean
    private JwtDecoder jwtDecoder;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @org.junit.jupiter.api.BeforeEach
    void setUpIdempotencyGuard() {
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(6)).get())
                .when(idempotencyGuard)
                .executeRequired(anyString(), any(UUID.class), anyString(), anyString(), any(), any(), any());
    }

    @Test
    void walletApisShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/wallet/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(get("/api/wallet/transactions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(post("/api/wallet/recharges")
                        .contentType("application/json")
                        .content("""
                                {
                                  "amount": 1200
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(post("/api/wallet/withdrawals")
                        .contentType("application/json")
                        .content("""
                                {
                                  "amount": 500
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(post("/api/wallet/transfers")
                        .contentType("application/json")
                        .content("""
                                {
                                  "toUserId": "%s",
                                  "amount": 300
                                }
                                """.formatted(uuid(2))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void walletSummaryShouldReturnCurrentBalanceForAuthenticatedUser() throws Exception {
        UUID userId = uuid(1);
        when(accountService.balanceOfUser(userId)).thenReturn(2300L);
        when(accountService.statusOfUser(userId)).thenReturn("ACTIVE");

        mockMvc.perform(get("/api/wallet/summary")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()).claim("username", "u1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.balance").value(2300))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void walletTransactionsShouldReturnCurrentUserLedgerRows() throws Exception {
        UUID userId = uuid(1);
        UUID counterpartUserId = uuid(2);
        UUID accountId = UUID.fromString("00000000-0000-7000-8000-000000000721");
        UUID txnId = UUID.fromString("00000000-0000-7000-8000-000000000722");
        WalletAccount account = userAccount(accountId, userId, 975L);
        when(accountService.findUserWallet(userId)).thenReturn(account);
        when(ledgerService.recentTransactions(eq(account), eq(12))).thenReturn(List.of(
                new WalletTransactionResult(
                        txnId,
                        "wallet:transfer:api-test",
                        "TRANSFER",
                        "TRANSFER",
                        "order-api-test",
                        "SUCCEEDED",
                        -25L,
                        975L,
                        "用户 " + counterpartUserId,
                        null,
                        new Date(1779100000000L)
                )
        ));

        mockMvc.perform(get("/api/wallet/transactions")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()).claim("username", "u1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].txnId").value(txnId.toString()))
                .andExpect(jsonPath("$.data[0].txnRef").value("wallet:transfer:api-test"))
                .andExpect(jsonPath("$.data[0].txnType").value("TRANSFER"))
                .andExpect(jsonPath("$.data[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data[0].amount").value(-25))
                .andExpect(jsonPath("$.data[0].balanceAfter").value(975))
                .andExpect(jsonPath("$.data[0].counterpartLabel").value("用户 " + counterpartUserId));
    }

    @Test
    void rechargeEndpointShouldReturnRechargeResultForAuthenticatedUser() throws Exception {
        UUID userId = uuid(1);
        UUID orderId = UUID.fromString("00000000-0000-7000-8000-000000000623");
        when(rechargeService.complete(eq("recharge:req-api-1"), eq(userId), eq(1200L)))
                .thenReturn(new RechargeOrderResult(orderId, "recharge:req-api-1", userId, 1200L, "PAID"));

        mockMvc.perform(post("/api/wallet/recharges")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()).claim("username", "u1")))
                        .header(IdempotencyGuard.HEADER_IDEMPOTENCY_KEY, "recharge:req-api-1")
                        .contentType("application/json")
                        .content("""
                                {
                                  "amount": 1200
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.data.requestId").value("recharge:req-api-1"))
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.amount").value(1200))
                .andExpect(jsonPath("$.data.status").value("PAID"));
    }

    @Test
    void rechargeEndpointShouldAcceptIdempotencyKeyHeaderWithoutBodyRequestId() throws Exception {
        UUID userId = uuid(1);
        UUID orderId = UUID.fromString("00000000-0000-7000-8000-000000000623");
        when(rechargeService.complete(eq("recharge:header-api-1"), eq(userId), eq(1200L)))
                .thenReturn(new RechargeOrderResult(orderId, "recharge:header-api-1", userId, 1200L, "PAID"));

        mockMvc.perform(post("/api/wallet/recharges")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()).claim("username", "u1")))
                        .header(IdempotencyGuard.HEADER_IDEMPOTENCY_KEY, "recharge:header-api-1")
                        .contentType("application/json")
                        .content("""
                                {
                                  "amount": 1200
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestId").value("recharge:header-api-1"));
    }

    @Test
    void withdrawEndpointShouldReturnWithdrawResultForAuthenticatedUser() throws Exception {
        UUID userId = uuid(1);
        UUID orderId = UUID.fromString("00000000-0000-7000-8000-000000000624");
        when(withdrawService.request(eq("withdraw:req-api-1"), eq(userId), eq(500L)))
                .thenReturn(new WithdrawOrderResult(orderId, "withdraw:req-api-1", userId, 500L, "SUCCEEDED"));

        mockMvc.perform(post("/api/wallet/withdrawals")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()).claim("username", "u1")))
                        .header(IdempotencyGuard.HEADER_IDEMPOTENCY_KEY, "withdraw:req-api-1")
                        .contentType("application/json")
                        .content("""
                                {
                                  "amount": 500
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.data.requestId").value("withdraw:req-api-1"))
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.amount").value(500))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
    }

    @Test
    void withdrawEndpointShouldAcceptIdempotencyKeyHeaderWithoutBodyRequestId() throws Exception {
        UUID userId = uuid(1);
        UUID orderId = UUID.fromString("00000000-0000-7000-8000-000000000624");
        when(withdrawService.request(eq("withdraw:header-api-1"), eq(userId), eq(500L)))
                .thenReturn(new WithdrawOrderResult(orderId, "withdraw:header-api-1", userId, 500L, "SUCCEEDED"));

        mockMvc.perform(post("/api/wallet/withdrawals")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()).claim("username", "u1")))
                        .header(IdempotencyGuard.HEADER_IDEMPOTENCY_KEY, "withdraw:header-api-1")
                        .contentType("application/json")
                        .content("""
                                {
                                  "amount": 500
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestId").value("withdraw:header-api-1"));
    }

    @Test
    void transferEndpointShouldReturnTransferResultForAuthenticatedUserWithUuidOrderId() throws Exception {
        UUID fromUserId = uuid(1);
        UUID toUserId = uuid(2);
        UUID orderId = UUID.fromString("00000000-0000-7000-8000-000000000625");
        when(transferService.create(eq("transfer:req-api-1"), eq(fromUserId), eq(toUserId), eq(300L)))
                .thenReturn(transferResponse(orderId, "transfer:req-api-1", fromUserId, toUserId, 300L, "SUCCEEDED"));

        mockMvc.perform(post("/api/wallet/transfers")
                        .with(jwt().jwt(jwt -> jwt.subject(fromUserId.toString()).claim("username", "u1")))
                        .header(IdempotencyGuard.HEADER_IDEMPOTENCY_KEY, "transfer:req-api-1")
                        .contentType("application/json")
                        .content("""
                                {
                                  "toUserId": "%s",
                                  "amount": 300
                                }
                                """.formatted(toUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.data.requestId").value("transfer:req-api-1"))
                .andExpect(jsonPath("$.data.fromUserId").value(fromUserId.toString()))
                .andExpect(jsonPath("$.data.toUserId").value(toUserId.toString()))
                .andExpect(jsonPath("$.data.amount").value(300))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
    }

    @Test
    void transferEndpointShouldAcceptIdempotencyKeyHeaderWithoutBodyRequestId() throws Exception {
        UUID fromUserId = uuid(1);
        UUID toUserId = uuid(2);
        UUID orderId = UUID.fromString("00000000-0000-7000-8000-000000000625");
        when(transferService.create(eq("transfer:header-api-1"), eq(fromUserId), eq(toUserId), eq(300L)))
                .thenReturn(transferResponse(orderId, "transfer:header-api-1", fromUserId, toUserId, 300L, "SUCCEEDED"));

        mockMvc.perform(post("/api/wallet/transfers")
                        .with(jwt().jwt(jwt -> jwt.subject(fromUserId.toString()).claim("username", "u1")))
                        .header(IdempotencyGuard.HEADER_IDEMPOTENCY_KEY, "transfer:header-api-1")
                        .contentType("application/json")
                        .content("""
                                {
                                  "toUserId": "%s",
                                  "amount": 300
                                }
                                """.formatted(toUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestId").value("transfer:header-api-1"));
    }

    @Test
    void rechargeEndpointShouldRejectBodyRequestId() throws Exception {
        UUID userId = uuid(1);

        mockMvc.perform(post("/api/wallet/recharges")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()).claim("username", "u1")))
                        .header(IdempotencyGuard.HEADER_IDEMPOTENCY_KEY, "recharge:req-api-1")
                        .contentType("application/json")
                        .content("""
                                {
                                  "requestId": "recharge:req-api-1",
                                  "amount": 1200
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void rechargeEndpointShouldReturnConflictForReplayPayloadMismatch() throws Exception {
        UUID userId = uuid(1);
        when(rechargeService.complete(eq("recharge:req-conflict"), eq(userId), eq(1200L)))
                .thenThrow(new BusinessException(WalletErrorCode.REQUEST_REPLAY_CONFLICT, "requestId already used by another recharge"));

        mockMvc.perform(post("/api/wallet/recharges")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()).claim("username", "u1")))
                        .header(IdempotencyGuard.HEADER_IDEMPOTENCY_KEY, "recharge:req-conflict")
                        .contentType("application/json")
                        .content("""
                                {
                                  "amount": 1200
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(WalletErrorCode.REQUEST_REPLAY_CONFLICT.getCode()));
    }

    @Test
    void withdrawEndpointShouldReturnConflictForReplayPayloadMismatch() throws Exception {
        UUID userId = uuid(1);
        when(withdrawService.request(eq("withdraw:req-conflict"), eq(userId), eq(500L)))
                .thenThrow(new BusinessException(WalletErrorCode.REQUEST_REPLAY_CONFLICT, "requestId already used by another withdraw"));

        mockMvc.perform(post("/api/wallet/withdrawals")
                        .with(jwt().jwt(jwt -> jwt.subject(userId.toString()).claim("username", "u1")))
                        .header(IdempotencyGuard.HEADER_IDEMPOTENCY_KEY, "withdraw:req-conflict")
                        .contentType("application/json")
                        .content("""
                                {
                                  "amount": 500
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(WalletErrorCode.REQUEST_REPLAY_CONFLICT.getCode()));
    }

    @Test
    void transferEndpointShouldReturnConflictForReplayPayloadMismatch() throws Exception {
        UUID fromUserId = uuid(1);
        UUID toUserId = uuid(2);
        when(transferService.create(eq("transfer:req-conflict"), eq(fromUserId), eq(toUserId), eq(300L)))
                .thenThrow(new BusinessException(WalletErrorCode.REQUEST_REPLAY_CONFLICT, "requestId already used by another transfer"));

        mockMvc.perform(post("/api/wallet/transfers")
                        .with(jwt().jwt(jwt -> jwt.subject(fromUserId.toString()).claim("username", "u1")))
                        .header(IdempotencyGuard.HEADER_IDEMPOTENCY_KEY, "transfer:req-conflict")
                        .contentType("application/json")
                        .content("""
                                {
                                  "toUserId": "%s",
                                  "amount": 300
                                }
                                """.formatted(toUserId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(WalletErrorCode.REQUEST_REPLAY_CONFLICT.getCode()));
    }

    private TransferOrderResult transferResponse(UUID orderId,
                                                 String requestId,
                                                 UUID fromUserId,
                                                 UUID toUserId,
                                                 long amount,
                                                 String status) {
        return new TransferOrderResult(orderId, requestId, fromUserId, toUserId, amount, status);
    }

    private WalletAccount userAccount(UUID accountId, UUID userId, long balance) {
        WalletAccount account = new WalletAccount();
        account.setAccountId(accountId);
        account.setOwnerType("USER");
        account.setOwnerId(userId);
        account.setAccountType("USER_WALLET");
        account.setBalance(balance);
        account.setStatus("ACTIVE");
        account.setVersion(0L);
        return account;
    }
}
