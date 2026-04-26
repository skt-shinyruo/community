package com.nowcoder.community.wallet.service;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.wallet.entity.TransferOrder;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import com.nowcoder.community.wallet.mapper.TransferOrderMapper;
import com.nowcoder.community.wallet.model.TransferOrderResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Method;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class TransferServiceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransferService transferService;

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
        jdbcTemplate.update("delete from transfer_order");
        jdbcTemplate.update("delete from wallet_account");
    }

    @Test
    void transferShouldDebitSenderAndCreditReceiverOnceAndPersistUuidv7OrderId() throws Exception {
        UUID fromUserId = uuid(101);
        UUID toUserId = uuid(202);
        seedUserBalance(fromUserId, 900);

        TransferOrderResult result = transferService.create("transfer:req-1", fromUserId, toUserId, 300);

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(accountService.balanceOfUser(fromUserId)).isEqualTo(600);
        assertThat(accountService.balanceOfUser(toUserId)).isEqualTo(300);
        assertThat(countRows("transfer_order")).isEqualTo(1);
        assertThat(countRows("wallet_txn")).isEqualTo(1);
        assertThat(countRows("wallet_entry")).isEqualTo(2);

        Method orderIdGetter = TransferOrderResult.class.getMethod("orderId");
        Object orderId = orderIdGetter.invoke(result);
        assertThat(orderId).isInstanceOf(UUID.class);
        UUID parsedOrderId = (UUID) orderId;
        assertThat(parsedOrderId.version()).isEqualTo(7);

        byte[] storedOrderId = jdbcTemplate.queryForObject(
                "select order_id from transfer_order where request_id = ?",
                (rs, rowNum) -> rs.getBytes(1),
                "transfer:req-1"
        );
        assertThat(storedOrderId).hasSize(16);
        assertThat(BinaryUuidCodec.fromBytes(storedOrderId)).isEqualTo(parsedOrderId);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from wallet_txn where request_id = ?",
                Integer.class,
                "wallet:transfer:" + parsedOrderId
        )).isEqualTo(1);
    }

    @Test
    void duplicateTransferRequestShouldBeIdempotent() {
        UUID fromUserId = uuid(101);
        UUID toUserId = uuid(202);
        seedUserBalance(fromUserId, 900);

        TransferOrderResult first = transferService.create("transfer:req-1", fromUserId, toUserId, 300);
        TransferOrderResult second = transferService.create("transfer:req-1", fromUserId, toUserId, 300);

        assertThat(second.orderId()).isEqualTo(first.orderId());
        assertThat(accountService.balanceOfUser(fromUserId)).isEqualTo(600);
        assertThat(accountService.balanceOfUser(toUserId)).isEqualTo(300);
        assertThat(countRows("transfer_order")).isEqualTo(1);
        assertThat(countRows("wallet_txn")).isEqualTo(1);
        assertThat(countRows("wallet_entry")).isEqualTo(2);
    }

    @Test
    void transferShouldRejectReplayWhenReceiverDoesNotMatchExistingOrder() {
        UUID fromUserId = uuid(101);
        seedUserBalance(fromUserId, 900);
        transferService.create("transfer:req-receiver-mismatch", fromUserId, uuid(202), 300);

        assertThatThrownBy(() -> transferService.create("transfer:req-receiver-mismatch", fromUserId, uuid(303), 300))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(WalletErrorCode.REQUEST_REPLAY_CONFLICT))
                .hasMessageContaining("requestId");
    }

    @Test
    void transferShouldAllowSameRequestIdForDifferentSenders() {
        UUID firstFromUserId = uuid(101);
        UUID secondFromUserId = uuid(102);
        UUID toUserId = uuid(202);
        seedUserBalance(firstFromUserId, 900);
        seedUserBalance(secondFromUserId, 900);

        TransferOrderResult first = transferService.create("transfer:req-shared-senders", firstFromUserId, toUserId, 300);
        TransferOrderResult second = transferService.create("transfer:req-shared-senders", secondFromUserId, toUserId, 300);

        assertThat(second.orderId()).isNotEqualTo(first.orderId());
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from transfer_order where request_id = ?",
                Integer.class,
                "transfer:req-shared-senders"
        )).isEqualTo(2);
    }

    @Test
    void transferShouldRejectReplayWhenAmountDoesNotMatchExistingOrder() {
        UUID fromUserId = uuid(101);
        UUID toUserId = uuid(202);
        seedUserBalance(fromUserId, 900);
        transferService.create("transfer:req-amount-mismatch", fromUserId, toUserId, 300);

        assertThatThrownBy(() -> transferService.create("transfer:req-amount-mismatch", fromUserId, toUserId, 400))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(WalletErrorCode.REQUEST_REPLAY_CONFLICT))
                .hasMessageContaining("requestId");
    }

    @Test
    void transferShouldRejectTransfersToSelf() {
        UUID userId = uuid(101);
        seedUserBalance(userId, 900);

        assertThatThrownBy(() -> transferService.create("transfer:req-self", userId, userId, 300))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(WalletErrorCode.INVALID_TRANSFER))
                .hasMessageContaining("self");
    }

    @Test
    void transferShouldLoadExistingOrderWhenInsertLosesDuplicateKeyRace() {
        TransferOrderMapper mapper = mock(TransferOrderMapper.class);
        WalletAccountService mockedAccountService = mock(WalletAccountService.class);
        WalletLedgerService mockedLedgerService = mock(WalletLedgerService.class);
        TransferService service = new TransferService(mapper, mockedAccountService, mockedLedgerService);

        UUID fromUserId = uuid(101);
        UUID toUserId = uuid(202);
        UUID orderId = UUID.fromString("00000000-0000-7000-8000-000000000642");
        TransferOrder succeededOrder = order(orderId, "transfer:req-race", fromUserId, toUserId, 300, "SUCCEEDED");

        when(mapper.selectByFromUserIdAndRequestId(fromUserId, "transfer:req-race"))
                .thenReturn(null, succeededOrder);
        when(mockedAccountService.ensureUserWallet(fromUserId))
                .thenReturn(UUID.fromString("00000000-0000-7000-8000-000000000643"));
        when(mockedAccountService.ensureUserWallet(toUserId))
                .thenReturn(UUID.fromString("00000000-0000-7000-8000-000000000644"));
        org.mockito.Mockito.doThrow(new DuplicateKeyException("duplicate request"))
                .when(mapper).insert(any(TransferOrder.class));

        TransferOrderResult result = service.create("transfer:req-race", fromUserId, toUserId, 300);

        assertThat(readOrderId(result)).isEqualTo(orderId);
        assertThat(result.status()).isEqualTo("SUCCEEDED");
        verify(mockedLedgerService, never()).post(any());
    }

    private void seedUserBalance(UUID userId, long balance) {
        UUID accountId = accountService.ensureUserWallet(userId);
        jdbcTemplate.update(
                "update wallet_account set balance = ?, version = 0 where account_id = ?",
                balance,
                accountId
        );
    }

    private int countRows(String tableName) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from " + tableName, Integer.class);
        return count == null ? 0 : count;
    }

    private TransferOrder order(UUID orderId, String requestId, UUID fromUserId, UUID toUserId, long amount, String status) {
        TransferOrder order = new TransferOrder();
        order.setOrderId(orderId);
        order.setRequestId(requestId);
        order.setFromUserId(fromUserId);
        order.setToUserId(toUserId);
        order.setAmount(amount);
        order.setStatus(status);
        return order;
    }

    private UUID readOrderId(TransferOrderResult response) {
        try {
            return (UUID) TransferOrderResult.class.getMethod("orderId").invoke(response);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }
}
