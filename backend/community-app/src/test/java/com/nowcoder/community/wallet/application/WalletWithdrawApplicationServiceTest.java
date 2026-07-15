package com.nowcoder.community.wallet.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.wallet.domain.model.WithdrawOrder;
import com.nowcoder.community.wallet.domain.repository.CreationOutcome;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import com.nowcoder.community.wallet.domain.repository.WithdrawOrderRepository;
import com.nowcoder.community.wallet.domain.model.WalletLedgerCommand;
import com.nowcoder.community.wallet.domain.model.WalletTxnType;
import com.nowcoder.community.wallet.application.result.WithdrawOrderResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class WalletWithdrawApplicationServiceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WalletWithdrawApplicationService withdrawService;

    @Autowired
    private WalletAccountApplicationService accountService;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from wallet_entry");
        jdbcTemplate.update("delete from wallet_txn");
        jdbcTemplate.update("delete from recharge_order");
        jdbcTemplate.update("delete from withdraw_order");
        jdbcTemplate.update("delete from transfer_order");
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
    void withdrawShouldRejectNullCommand() {
        assertThatThrownBy(() -> withdrawService.withdraw(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
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
        assertThat(walletTxnRequestIds(WalletTxnType.WITHDRAW))
                .containsExactlyInAnyOrder(
                        "wallet:withdraw:" + parsedOrderId + ":request",
                        "wallet:withdraw:" + parsedOrderId + ":settle"
                );
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
    void requestWithdrawShouldAllowSameRequestIdForDifferentUsers() {
        UUID firstUserId = uuid(101);
        UUID secondUserId = uuid(202);
        seedUserBalance(firstUserId, 2000);
        seedUserBalance(secondUserId, 3000);
        seedSystemBalance("PLATFORM_CASH", 2000);

        WithdrawOrderResult first = withdrawService.request("withdraw:req-shared", firstUserId, 500);
        WithdrawOrderResult second = withdrawService.request("withdraw:req-shared", secondUserId, 700);

        assertThat(second.orderId()).isNotEqualTo(first.orderId());
        assertThat(countRows("withdraw_order")).isEqualTo(2);
        assertThat(countRows("wallet_txn")).isEqualTo(4);
        assertThat(walletTxnRequestIds(WalletTxnType.WITHDRAW))
                .containsExactlyInAnyOrder(
                        "wallet:withdraw:" + first.orderId() + ":request",
                        "wallet:withdraw:" + first.orderId() + ":settle",
                        "wallet:withdraw:" + second.orderId() + ":request",
                        "wallet:withdraw:" + second.orderId() + ":settle"
                );
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
        WithdrawOrderRepository repository = mock(WithdrawOrderRepository.class);
        WalletAccountApplicationService mockedAccountService = mock(WalletAccountApplicationService.class);
        WalletLedgerApplicationService mockedLedgerService = mock(WalletLedgerApplicationService.class);
        WalletWithdrawApplicationService service = new WalletWithdrawApplicationService(repository, mockedAccountService, mockedLedgerService);

        UUID userId = uuid(101);
        UUID orderId = UUID.fromString("00000000-0000-7000-8000-000000000632");
        WithdrawOrder requestedOrder = order(orderId, "withdraw:req-race", userId, 500, "REQUESTED");
        WithdrawOrder processingOrder = order(orderId, "withdraw:req-race", userId, 500, "PROCESSING");
        WithdrawOrder succeededOrder = order(orderId, "withdraw:req-race", userId, 500, "SUCCEEDED");

        when(repository.findByUserIdAndRequestId(userId, "withdraw:req-race"))
                .thenReturn(null, processingOrder, succeededOrder);
        when(repository.create(any(WithdrawOrder.class)))
                .thenReturn(CreationOutcome.alreadyExists(requestedOrder));
        when(mockedAccountService.balanceOfSystem("PLATFORM_CASH")).thenReturn(800L);
        when(mockedAccountService.ensureUserWallet(userId))
                .thenReturn(UUID.fromString("00000000-0000-7000-8000-000000000634"));
        when(mockedAccountService.ensureSystemAccount("WITHDRAW_PENDING"))
                .thenReturn(UUID.fromString("00000000-0000-7000-8000-000000000635"));
        when(mockedAccountService.ensureSystemAccount("PLATFORM_CASH"))
                .thenReturn(UUID.fromString("00000000-0000-7000-8000-000000000636"));
        WithdrawOrderResult result = service.request("withdraw:req-race", userId, 500);

        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.status()).isEqualTo("SUCCEEDED");
        ArgumentCaptor<WalletLedgerCommand> commandCaptor = ArgumentCaptor.forClass(WalletLedgerCommand.class);
        verify(mockedLedgerService, org.mockito.Mockito.times(2)).post(commandCaptor.capture());
        assertThat(commandCaptor.getAllValues())
                .extracting(WalletLedgerCommand::requestId)
                .containsExactly(
                        "wallet:withdraw:" + orderId + ":request",
                        "wallet:withdraw:" + orderId + ":settle"
                );
        assertThat(commandCaptor.getAllValues())
                .extracting(WalletLedgerCommand::bizId)
                .containsOnly(orderId.toString());
        verify(repository).updateStatus(userId, "withdraw:req-race", "REQUESTED", "PROCESSING");
        verify(repository).updateStatus(userId, "withdraw:req-race", "PROCESSING", "SUCCEEDED");
    }

    @Test
    void requestWithdrawShouldReturnExistingOrderWhenReplayBecomesVisibleDuringInsufficientCashCheck() {
        WithdrawOrderRepository repository = mock(WithdrawOrderRepository.class);
        WalletAccountApplicationService mockedAccountService = mock(WalletAccountApplicationService.class);
        WalletLedgerApplicationService mockedLedgerService = mock(WalletLedgerApplicationService.class);
        WalletWithdrawApplicationService service = new WalletWithdrawApplicationService(repository, mockedAccountService, mockedLedgerService);

        UUID userId = uuid(101);
        UUID orderId = UUID.fromString("00000000-0000-7000-8000-000000000633");
        WithdrawOrder succeededOrder = order(orderId, "withdraw:req-race-cash", userId, 500, "SUCCEEDED");

        when(repository.findByUserIdAndRequestId(userId, "withdraw:req-race-cash"))
                .thenReturn(null, succeededOrder);
        when(mockedAccountService.balanceOfSystem("PLATFORM_CASH")).thenReturn(0L);

        WithdrawOrderResult result = service.request("withdraw:req-race-cash", userId, 500);

        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.status()).isEqualTo("SUCCEEDED");
        org.mockito.Mockito.verifyNoInteractions(mockedLedgerService);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).create(any(WithdrawOrder.class));
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

    private List<String> walletTxnRequestIds(WalletTxnType txnType) {
        return jdbcTemplate.queryForList(
                "select request_id from wallet_txn where txn_type = ? order by request_id",
                String.class,
                txnType.name()
        );
    }
}
