package com.nowcoder.community.wallet.service;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.wallet.dto.CreateWithdrawResponse;
import com.nowcoder.community.wallet.entity.WithdrawOrder;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import com.nowcoder.community.wallet.mapper.WithdrawOrderMapper;
import com.nowcoder.community.wallet.model.WalletTxnType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

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
        seedUserBalance(101, 2000);

        assertThatThrownBy(() -> withdrawService.request("withdraw:req-1", 101, 500))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cash");
    }

    @Test
    void requestWithdrawShouldMoveMoneyToPendingThenSettleToPlatformCash() {
        seedUserBalance(101, 2000);
        seedSystemBalance("PLATFORM_CASH", 800);

        CreateWithdrawResponse result = withdrawService.request("withdraw:req-success", 101, 500);

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(accountService.balanceOfUser(101)).isEqualTo(1500);
        assertThat(accountService.balanceOfSystem("WITHDRAW_PENDING")).isZero();
        assertThat(accountService.balanceOfSystem("PLATFORM_CASH")).isEqualTo(300);
        assertThat(countRows("withdraw_order")).isEqualTo(1);
        assertThat(countRows("wallet_txn")).isEqualTo(2);
        assertThat(countRows("wallet_entry")).isEqualTo(4);
    }

    @Test
    void requestWithdrawShouldReturnExistingOrderForSameRequestIdAndPayloadEvenIfPlatformCashLaterDrops() {
        seedUserBalance(101, 2000);
        seedSystemBalance("PLATFORM_CASH", 800);

        CreateWithdrawResponse first = withdrawService.request("withdraw:req-replay", 101, 500);
        seedSystemBalance("PLATFORM_CASH", 0);

        CreateWithdrawResponse second = withdrawService.request("withdraw:req-replay", 101, 500);

        assertThat(second.orderId()).isEqualTo(first.orderId());
        assertThat(second.status()).isEqualTo("SUCCEEDED");
        assertThat(accountService.balanceOfUser(101)).isEqualTo(1500);
        assertThat(accountService.balanceOfSystem("PLATFORM_CASH")).isZero();
        assertThat(accountService.balanceOfSystem("WITHDRAW_PENDING")).isZero();
        assertThat(countRows("withdraw_order")).isEqualTo(1);
        assertThat(countRows("wallet_txn")).isEqualTo(2);
        assertThat(countRows("wallet_entry")).isEqualTo(4);
    }

    @Test
    void requestWithdrawShouldRejectReplayWhenUserIdDoesNotMatchExistingOrder() {
        seedUserBalance(101, 2000);
        seedSystemBalance("PLATFORM_CASH", 800);
        withdrawService.request("withdraw:req-user-mismatch", 101, 500);

        assertThatThrownBy(() -> withdrawService.request("withdraw:req-user-mismatch", 202, 500))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(WalletErrorCode.REQUEST_REPLAY_CONFLICT))
                .hasMessageContaining("requestId");
    }

    @Test
    void requestWithdrawShouldRejectReplayWhenAmountDoesNotMatchExistingOrder() {
        seedUserBalance(101, 2000);
        seedSystemBalance("PLATFORM_CASH", 800);
        withdrawService.request("withdraw:req-amount-mismatch", 101, 500);

        assertThatThrownBy(() -> withdrawService.request("withdraw:req-amount-mismatch", 101, 700))
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

        WithdrawOrder requestedOrder = order(20L, "withdraw:req-race", 101, 500, "REQUESTED");
        WithdrawOrder processingOrder = order(20L, "withdraw:req-race", 101, 500, "PROCESSING");
        WithdrawOrder succeededOrder = order(20L, "withdraw:req-race", 101, 500, "SUCCEEDED");

        when(mapper.selectByRequestId("withdraw:req-race"))
                .thenReturn(null, requestedOrder, processingOrder, succeededOrder);
        when(mockedAccountService.balanceOfSystem("PLATFORM_CASH")).thenReturn(800L);
        when(mockedAccountService.ensureUserWallet(101)).thenReturn(1L);
        when(mockedAccountService.ensureSystemAccount("WITHDRAW_PENDING")).thenReturn(2L);
        when(mockedAccountService.ensureSystemAccount("PLATFORM_CASH")).thenReturn(3L);
        org.mockito.Mockito.doThrow(new DuplicateKeyException("duplicate request"))
                .when(mapper).insert(any(WithdrawOrder.class));

        CreateWithdrawResponse result = service.request("withdraw:req-race", 101, 500);

        assertThat(result.orderId()).isEqualTo(20L);
        assertThat(result.status()).isEqualTo("SUCCEEDED");
        verify(mockedLedgerService).post(eq("withdraw:req-race:request"), eq(WalletTxnType.WITHDRAW), anyList());
        verify(mockedLedgerService).post(eq("withdraw:req-race:settle"), eq(WalletTxnType.WITHDRAW), anyList());
        verify(mapper).updateStatus("withdraw:req-race", "REQUESTED", "PROCESSING");
        verify(mapper).updateStatus("withdraw:req-race", "PROCESSING", "SUCCEEDED");
    }

    private void seedUserBalance(int userId, long balance) {
        long accountId = accountService.ensureUserWallet(userId);
        jdbcTemplate.update(
                "update wallet_account set balance = ?, version = 0 where account_id = ?",
                balance,
                accountId
        );
    }

    private void seedSystemBalance(String accountType, long balance) {
        long accountId = accountService.ensureSystemAccount(accountType);
        jdbcTemplate.update(
                "update wallet_account set balance = ?, version = 0 where account_id = ?",
                balance,
                accountId
        );
    }

    private WithdrawOrder order(long orderId, String requestId, long userId, long amount, String status) {
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
