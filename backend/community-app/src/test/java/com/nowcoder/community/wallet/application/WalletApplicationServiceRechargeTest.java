package com.nowcoder.community.wallet.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.wallet.domain.model.RechargeOrder;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import com.nowcoder.community.wallet.domain.repository.RechargeOrderRepository;
import com.nowcoder.community.wallet.application.result.RechargeOrderResult;
import com.nowcoder.community.wallet.domain.model.WalletLedgerCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.ActiveProfiles;

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
class WalletApplicationServiceRechargeTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WalletRechargeApplicationService rechargeService;

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
    void completeRechargeShouldCreditUserWalletOnceAndPersistUuidv7OrderId() {
        UUID userId = uuid(101);
        RechargeOrderResult result = rechargeService.complete("recharge:req-1", userId, 1200);

        assertThat(result.status()).isEqualTo("PAID");
        assertThat(accountService.balanceOfUser(userId)).isEqualTo(1200);
        UUID parsedOrderId = result.orderId();
        assertThat(parsedOrderId.version()).isEqualTo(7);

        byte[] storedOrderId = jdbcTemplate.queryForObject(
                "select order_id from recharge_order where request_id = ?",
                (rs, rowNum) -> rs.getBytes(1),
                "recharge:req-1"
        );
        assertThat(storedOrderId).hasSize(16);
        assertThat(BinaryUuidCodec.fromBytes(storedOrderId)).isEqualTo(parsedOrderId);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from wallet_txn where request_id = ?",
                Integer.class,
                "wallet:recharge:" + result.orderId()
        )).isEqualTo(1);
    }

    @Test
    void completeRechargeShouldReturnExistingOrderForSameRequestIdAndPayload() {
        UUID userId = uuid(101);
        RechargeOrderResult first = rechargeService.complete("recharge:req-replay", userId, 1200);

        RechargeOrderResult second = rechargeService.complete("recharge:req-replay", userId, 1200);

        assertThat(second.orderId()).isEqualTo(first.orderId());
        assertThat(second.status()).isEqualTo("PAID");
        assertThat(accountService.balanceOfUser(userId)).isEqualTo(1200);
        assertThat(countRows("recharge_order")).isEqualTo(1);
        assertThat(countRows("wallet_txn")).isEqualTo(1);
        assertThat(countRows("wallet_entry")).isEqualTo(2);
    }

    @Test
    void completeRechargeShouldAllowSameRequestIdForDifferentUsers() {
        RechargeOrderResult first = rechargeService.complete("recharge:req-shared-users", uuid(101), 1200);
        RechargeOrderResult second = rechargeService.complete("recharge:req-shared-users", uuid(202), 1200);

        assertThat(second.orderId()).isNotEqualTo(first.orderId());
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from recharge_order where request_id = ?",
                Integer.class,
                "recharge:req-shared-users"
        )).isEqualTo(2);
    }

    @Test
    void completeRechargeShouldRejectReplayWhenAmountDoesNotMatchExistingOrder() {
        rechargeService.complete("recharge:req-amount-mismatch", uuid(101), 1200);

        assertThatThrownBy(() -> rechargeService.complete("recharge:req-amount-mismatch", uuid(101), 1300))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(WalletErrorCode.REQUEST_REPLAY_CONFLICT))
                .hasMessageContaining("requestId");
    }

    @Test
    void completeRechargeShouldLoadExistingOrderWhenInsertLosesDuplicateKeyRace() {
        RechargeOrderRepository repository = mock(RechargeOrderRepository.class);
        WalletAccountApplicationService mockedAccountService = mock(WalletAccountApplicationService.class);
        WalletLedgerApplicationService mockedLedgerService = mock(WalletLedgerApplicationService.class);
        WalletRechargeApplicationService service = new WalletRechargeApplicationService(repository, mockedAccountService, mockedLedgerService);

        UUID userId = uuid(101);
        UUID orderId = UUID.fromString("00000000-0000-7000-8000-000000000622");
        RechargeOrder createdOrder = order(orderId, "recharge:req-race", userId, 1200, "CREATED");
        RechargeOrder paidOrder = order(orderId, "recharge:req-race", userId, 1200, "PAID");

        when(repository.findByUserIdAndRequestId(userId, "recharge:req-race"))
                .thenReturn(null, createdOrder, paidOrder);
        when(mockedAccountService.ensureSystemAccount("PLATFORM_CASH"))
                .thenReturn(UUID.fromString("00000000-0000-7000-8000-000000000623"));
        when(mockedAccountService.ensureUserWallet(userId))
                .thenReturn(UUID.fromString("00000000-0000-7000-8000-000000000624"));
        org.mockito.Mockito.doThrow(new DuplicateKeyException("duplicate request"))
                .when(repository).insert(any(RechargeOrder.class));

        RechargeOrderResult result = service.complete("recharge:req-race", userId, 1200);

        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.status()).isEqualTo("PAID");
        org.mockito.ArgumentCaptor<WalletLedgerCommand> commandCaptor =
                org.mockito.ArgumentCaptor.forClass(WalletLedgerCommand.class);
        verify(mockedLedgerService).post(commandCaptor.capture());
        assertThat(commandCaptor.getValue().requestId()).isEqualTo("wallet:recharge:" + orderId);
        assertThat(commandCaptor.getValue().bizId()).isEqualTo(orderId.toString());
        verify(repository).updateStatus(userId, "recharge:req-race", "CREATED", "PAID");
    }

    private RechargeOrder order(UUID orderId, String requestId, UUID userId, long amount, String status) {
        RechargeOrder order = new RechargeOrder();
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
