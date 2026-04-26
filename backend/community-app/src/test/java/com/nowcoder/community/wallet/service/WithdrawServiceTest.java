package com.nowcoder.community.wallet.service;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.wallet.entity.WithdrawOrder;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import com.nowcoder.community.wallet.mapper.WithdrawOrderMapper;
import com.nowcoder.community.wallet.model.WalletTxnType;
import com.nowcoder.community.wallet.model.WithdrawOrderResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class WithdrawServiceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WithdrawService withdrawService;

    @Autowired
    private WalletAccountService accountService;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from reward_ledger");
        jdbcTemplate.update("delete from reward_account");
        jdbcTemplate.update("delete from wallet_entry");
        jdbcTemplate.update("delete from wallet_txn");
        jdbcTemplate.update("delete from recharge_order");
        jdbcTemplate.update("delete from withdraw_order");
        jdbcTemplate.update("delete from wallet_account");
    }

    @Test
    void requestWithdrawShouldMoveMoneyToPendingAndFailIfPlatformCashIsMissing() {
        UUID userId = uuid(101);
        seedUserBalance(userId, 2000);

        assertThatThrownBy(() -> withdrawService.request("withdraw:req-1", userId, 500))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cash");
    }

    @Test
    void requestWithdrawShouldMoveMoneyToPendingThenSettleToPlatformCashWithUuidv7OrderId() {
        UUID userId = uuid(101);
        seedUserBalance(userId, 2000);
        seedSystemBalance("PLATFORM_CASH", 800);

        WithdrawOrderResult result = withdrawService.request("withdraw:req-success", userId, 500);

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(accountService.balanceOfUser(userId)).isEqualTo(1500);
        assertThat(accountService.balanceOfSystem("WITHDRAW_PENDING")).isZero();
        assertThat(accountService.balanceOfSystem("PLATFORM_CASH")).isEqualTo(300);
        assertThat(countRows("withdraw_order")).isEqualTo(1);
        assertThat(countRows("wallet_txn")).isEqualTo(2);
        assertThat(countRows("wallet_entry")).isEqualTo(4);

        UUID parsedOrderId = result.orderId();
        assertThat(parsedOrderId.version()).isEqualTo(7);

        byte[] storedOrderId = jdbcTemplate.queryForObject(
                "select order_id from withdraw_order where request_id = ?",
                (rs, rowNum) -> rs.getBytes(1),
                "withdraw:req-success"
        );
        assertThat(storedOrderId).hasSize(16);
        assertThat(BinaryUuidCodec.fromBytes(storedOrderId)).isEqualTo(parsedOrderId);
    }

    @Test
    void requestWithdrawShouldReturnExistingOrderForSameRequestIdAndPayloadEvenIfPlatformCashLaterDrops() {
        UUID userId = uuid(101);
        seedUserBalance(userId, 2000);
        seedSystemBalance("PLATFORM_CASH", 800);

        WithdrawOrderResult first = withdrawService.request("withdraw:req-replay", userId, 500);
        seedSystemBalance("PLATFORM_CASH", 0);

        WithdrawOrderResult second = withdrawService.request("withdraw:req-replay", userId, 500);

        assertThat(second.orderId()).isEqualTo(first.orderId());
        assertThat(second.status()).isEqualTo("SUCCEEDED");
        assertThat(accountService.balanceOfUser(userId)).isEqualTo(1500);
        assertThat(accountService.balanceOfSystem("PLATFORM_CASH")).isZero();
        assertThat(accountService.balanceOfSystem("WITHDRAW_PENDING")).isZero();
        assertThat(countRows("withdraw_order")).isEqualTo(1);
        assertThat(countRows("wallet_txn")).isEqualTo(2);
        assertThat(countRows("wallet_entry")).isEqualTo(4);
    }

    @Test
    void requestWithdrawShouldRejectReplayWhenUserIdDoesNotMatchExistingOrder() {
        UUID userId = uuid(101);
        seedUserBalance(userId, 2000);
        seedSystemBalance("PLATFORM_CASH", 800);
        withdrawService.request("withdraw:req-user-mismatch", userId, 500);

        assertThatThrownBy(() -> withdrawService.request("withdraw:req-user-mismatch", uuid(202), 500))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(WalletErrorCode.REQUEST_REPLAY_CONFLICT))
                .hasMessageContaining("requestId");
    }

    @Test
    void requestWithdrawShouldRejectReplayWhenAmountDoesNotMatchExistingOrder() {
        UUID userId = uuid(101);
        seedUserBalance(userId, 2000);
        seedSystemBalance("PLATFORM_CASH", 800);
        withdrawService.request("withdraw:req-amount-mismatch", userId, 500);

        assertThatThrownBy(() -> withdrawService.request("withdraw:req-amount-mismatch", userId, 700))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(WalletErrorCode.REQUEST_REPLAY_CONFLICT))
                .hasMessageContaining("requestId");
    }

    @Test
    void requestWithdrawShouldLoadExistingOrderWhenInsertLosesDuplicateKeyRace() {
        WithdrawOrderMapper mapper = mock(WithdrawOrderMapper.class);
        WalletAccountService mockedAccountService = mock(WalletAccountService.class);
        WalletLedgerService mockedLedgerService = mock(WalletLedgerService.class);
        WithdrawService service = new WithdrawService(mapper, mockedAccountService, mockedLedgerService);

        UUID userId = uuid(101);
        UUID orderId = UUID.fromString("00000000-0000-7000-8000-000000000632");
        WithdrawOrder requestedOrder = order(orderId, "withdraw:req-race", userId, 500, "REQUESTED");
        WithdrawOrder processingOrder = order(orderId, "withdraw:req-race", userId, 500, "PROCESSING");
        WithdrawOrder succeededOrder = order(orderId, "withdraw:req-race", userId, 500, "SUCCEEDED");

        when(mapper.selectByRequestId("withdraw:req-race"))
                .thenReturn(null, requestedOrder, processingOrder, succeededOrder);
        when(mockedAccountService.balanceOfSystem("PLATFORM_CASH")).thenReturn(800L);
        when(mockedAccountService.ensureUserWallet(userId))
                .thenReturn(UUID.fromString("00000000-0000-7000-8000-000000000634"));
        when(mockedAccountService.ensureSystemAccount("WITHDRAW_PENDING"))
                .thenReturn(UUID.fromString("00000000-0000-7000-8000-000000000635"));
        when(mockedAccountService.ensureSystemAccount("PLATFORM_CASH"))
                .thenReturn(UUID.fromString("00000000-0000-7000-8000-000000000636"));
        org.mockito.Mockito.doThrow(new DuplicateKeyException("duplicate request"))
                .when(mapper).insert(any(WithdrawOrder.class));

        WithdrawOrderResult result = service.request("withdraw:req-race", userId, 500);

        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.status()).isEqualTo("SUCCEEDED");
        verify(mockedLedgerService).post(eq("withdraw:req-race:request"), eq(WalletTxnType.WITHDRAW), anyList());
        verify(mockedLedgerService).post(eq("withdraw:req-race:settle"), eq(WalletTxnType.WITHDRAW), anyList());
        verify(mapper).updateStatus("withdraw:req-race", "REQUESTED", "PROCESSING");
        verify(mapper).updateStatus("withdraw:req-race", "PROCESSING", "SUCCEEDED");
    }

    @Test
    void requestWithdrawShouldReturnExistingOrderWhenReplayBecomesVisibleDuringInsufficientCashCheck() {
        WithdrawOrderMapper mapper = mock(WithdrawOrderMapper.class);
        WalletAccountService mockedAccountService = mock(WalletAccountService.class);
        WalletLedgerService mockedLedgerService = mock(WalletLedgerService.class);
        WithdrawService service = new WithdrawService(mapper, mockedAccountService, mockedLedgerService);

        UUID userId = uuid(101);
        UUID orderId = UUID.fromString("00000000-0000-7000-8000-000000000633");
        WithdrawOrder succeededOrder = order(orderId, "withdraw:req-race-cash", userId, 500, "SUCCEEDED");

        when(mapper.selectByRequestId("withdraw:req-race-cash"))
                .thenReturn(null, succeededOrder);
        when(mockedAccountService.balanceOfSystem("PLATFORM_CASH")).thenReturn(0L);

        WithdrawOrderResult result = service.request("withdraw:req-race-cash", userId, 500);

        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.status()).isEqualTo("SUCCEEDED");
        org.mockito.Mockito.verifyNoInteractions(mockedLedgerService);
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.never()).insert(any(WithdrawOrder.class));
    }

    private void seedUserBalance(UUID userId, long balance) {
        UUID accountId = accountService.ensureUserWallet(userId);
        jdbcTemplate.update(
                "update wallet_account set balance = ?, version = 0 where account_id = ?",
                balance,
                accountId
        );
    }

    private void seedSystemBalance(String accountType, long balance) {
        UUID accountId = accountService.ensureSystemAccount(accountType);
        jdbcTemplate.update(
                "update wallet_account set balance = ?, version = 0 where account_id = ?",
                balance,
                accountId
        );
    }

    private WithdrawOrder order(UUID orderId, String requestId, UUID userId, long amount, String status) {
        WithdrawOrder order = new WithdrawOrder();
        order.setOrderId(orderId);
        order.setRequestId(requestId);
        order.setUserId(userId);
        order.setAmount(amount);
        order.setStatus(status);
        return order;
    }

    private int countRows(String tableName) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from " + tableName, Integer.class);
        return count == null ? 0 : count;
    }
}
