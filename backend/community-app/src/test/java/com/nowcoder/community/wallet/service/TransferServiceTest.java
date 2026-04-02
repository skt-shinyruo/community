package com.nowcoder.community.wallet.service;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.wallet.dto.CreateTransferResponse;
import com.nowcoder.community.wallet.entity.TransferOrder;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import com.nowcoder.community.wallet.mapper.TransferOrderMapper;
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
    void transferShouldDebitSenderAndCreditReceiverOnce() {
        seedUserBalance(101, 900);

        CreateTransferResponse result = transferService.create("transfer:req-1", 101, 202, 300);

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(accountService.balanceOfUser(101)).isEqualTo(600);
        assertThat(accountService.balanceOfUser(202)).isEqualTo(300);
        assertThat(countRows("transfer_order")).isEqualTo(1);
        assertThat(countRows("wallet_txn")).isEqualTo(1);
        assertThat(countRows("wallet_entry")).isEqualTo(2);
    }

    @Test
    void duplicateTransferRequestShouldBeIdempotent() {
        seedUserBalance(101, 900);

        CreateTransferResponse first = transferService.create("transfer:req-1", 101, 202, 300);
        CreateTransferResponse second = transferService.create("transfer:req-1", 101, 202, 300);

        assertThat(second.orderId()).isEqualTo(first.orderId());
        assertThat(accountService.balanceOfUser(101)).isEqualTo(600);
        assertThat(accountService.balanceOfUser(202)).isEqualTo(300);
        assertThat(countRows("transfer_order")).isEqualTo(1);
        assertThat(countRows("wallet_txn")).isEqualTo(1);
        assertThat(countRows("wallet_entry")).isEqualTo(2);
    }

    @Test
    void transferShouldRejectReplayWhenReceiverDoesNotMatchExistingOrder() {
        seedUserBalance(101, 900);
        transferService.create("transfer:req-receiver-mismatch", 101, 202, 300);

        assertThatThrownBy(() -> transferService.create("transfer:req-receiver-mismatch", 101, 303, 300))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(WalletErrorCode.REQUEST_REPLAY_CONFLICT))
                .hasMessageContaining("requestId");
    }

    @Test
    void transferShouldRejectReplayWhenAmountDoesNotMatchExistingOrder() {
        seedUserBalance(101, 900);
        transferService.create("transfer:req-amount-mismatch", 101, 202, 300);

        assertThatThrownBy(() -> transferService.create("transfer:req-amount-mismatch", 101, 202, 400))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(WalletErrorCode.REQUEST_REPLAY_CONFLICT))
                .hasMessageContaining("requestId");
    }

    @Test
    void transferShouldRejectTransfersToSelf() {
        seedUserBalance(101, 900);

        assertThatThrownBy(() -> transferService.create("transfer:req-self", 101, 101, 300))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(WalletErrorCode.INVALID_REQUEST))
                .hasMessageContaining("self");
    }

    @Test
    void transferShouldLoadExistingOrderWhenInsertLosesDuplicateKeyRace() {
        TransferOrderMapper mapper = mock(TransferOrderMapper.class);
        WalletAccountService mockedAccountService = mock(WalletAccountService.class);
        WalletLedgerService mockedLedgerService = mock(WalletLedgerService.class);
        TransferService service = new TransferService(mapper, mockedAccountService, mockedLedgerService);

        TransferOrder succeededOrder = order(40L, "transfer:req-race", 101, 202, 300, "SUCCEEDED");

        when(mapper.selectByRequestId("transfer:req-race"))
                .thenReturn(null, succeededOrder);
        when(mockedAccountService.ensureUserWallet(101)).thenReturn(1L);
        when(mockedAccountService.ensureUserWallet(202)).thenReturn(2L);
        org.mockito.Mockito.doThrow(new DuplicateKeyException("duplicate request"))
                .when(mapper).insert(any(TransferOrder.class));

        CreateTransferResponse result = service.create("transfer:req-race", 101, 202, 300);

        assertThat(result.orderId()).isEqualTo(40L);
        assertThat(result.status()).isEqualTo("SUCCEEDED");
        verify(mockedLedgerService).post(eq("transfer:req-race"), eq(WalletTxnType.TRANSFER), anyList());
    }

    private void seedUserBalance(int userId, long balance) {
        long accountId = accountService.ensureUserWallet(userId);
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

    private TransferOrder order(long orderId, String requestId, long fromUserId, long toUserId, long amount, String status) {
        TransferOrder order = new TransferOrder();
        order.setOrderId(orderId);
        order.setRequestId(requestId);
        order.setFromUserId(fromUserId);
        order.setToUserId(toUserId);
        order.setAmount(amount);
        order.setStatus(status);
        return order;
    }
}
