package com.nowcoder.community.wallet.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.wallet.application.command.CreateRechargeCommand;
import com.nowcoder.community.wallet.application.command.CreateWithdrawCommand;
import com.nowcoder.community.wallet.application.result.RechargeOrderResult;
import com.nowcoder.community.wallet.application.result.WithdrawOrderResult;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest(classes = CommunityAppApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class WalletTestCreditApplicationServiceTest {

    @Autowired
    private WalletRechargeApplicationService rechargeService;

    @Autowired
    private WalletWithdrawApplicationService withdrawService;

    @Autowired
    private WalletAccountApplicationService accountService;

    @Autowired
    private WalletTestCreditQuotaPort quotaPort;

    @SpyBean
    private WalletLedgerApplicationService ledgerService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        reset(ledgerService);
        jdbcTemplate.update("delete from http_idempotency");
        jdbcTemplate.update("delete from wallet_entry");
        jdbcTemplate.update("delete from wallet_txn");
        jdbcTemplate.update("delete from recharge_order");
        jdbcTemplate.update("delete from withdraw_order");
        jdbcTemplate.update("delete from transfer_order");
        jdbcTemplate.update("delete from wallet_test_credit_quota");
        jdbcTemplate.update("delete from wallet_account");
    }

    @AfterEach
    void resetLedgerService() {
        reset(ledgerService);
    }

    @Test
    void grantShouldUseTestCreditExpenseWithoutInventingPlatformCash() {
        UUID userId = uuid(101);

        RechargeOrderResult result = rechargeService.recharge(
                new CreateRechargeCommand(userId, 600L, "test-credit:grant:101")
        );

        assertThat(result.status()).isEqualTo("PAID");
        assertThat(result.requestId()).isEqualTo("test-credit:grant:101");
        assertThat(result.orderId().version()).isEqualTo(7);
        assertThat(accountService.balanceOfUser(userId)).isEqualTo(600L);
        assertThat(accountService.balanceOfSystem("PLATFORM_TEST_CREDIT_EXPENSE")).isEqualTo(600L);
        assertThat(accountService.balanceOfSystem("PLATFORM_CASH")).isZero();
        assertThat(txnTypeFor("wallet:test-credit:grant:%")).isEqualTo("TEST_CREDIT_GRANT");
    }

    @Test
    void discardShouldReverseOnlyCreditsGrantedToTheSameUserWithoutCreatingPayoutEntries() {
        UUID userId = uuid(101);
        rechargeService.recharge(new CreateRechargeCommand(userId, 600L, "test-credit:grant:discard"));

        WithdrawOrderResult result = withdrawService.withdraw(
                new CreateWithdrawCommand(userId, 250L, "test-credit:discard:101")
        );

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(accountService.balanceOfUser(userId)).isEqualTo(350L);
        assertThat(accountService.balanceOfSystem("PLATFORM_TEST_CREDIT_EXPENSE")).isEqualTo(350L);
        assertThat(accountService.balanceOfSystem("PLATFORM_CASH")).isZero();
        assertThat(accountService.balanceOfSystem("WITHDRAW_PENDING")).isZero();
        assertThat(txnTypeFor("wallet:test-credit:discard:%")).isEqualTo("TEST_CREDIT_DISCARD");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from wallet_txn where txn_type = 'WITHDRAW'",
                Integer.class
        )).isZero();
    }

    @Test
    void replayShouldReturnTheSameGrantWithoutConsumingQuotaTwice() {
        UUID userId = uuid(101);
        CreateRechargeCommand command = new CreateRechargeCommand(userId, 200L, "shared-key");

        RechargeOrderResult first = rechargeService.recharge(command);
        RechargeOrderResult second = rechargeService.recharge(command);

        assertThat(second.orderId()).isEqualTo(first.orderId());
        assertThat(accountService.balanceOfUser(userId)).isEqualTo(200L);
        assertThat(quotaPort.usage(userId).grantedAmount()).isEqualTo(200L);
        assertThat(countRows("recharge_order")).isEqualTo(1);
        assertThat(countRows("wallet_txn")).isEqualTo(1);
        assertThat(countRows("wallet_entry")).isEqualTo(2);
    }

    @Test
    void sameGrantKeyWithDifferentAmountShouldBeRejected() {
        UUID userId = uuid(101);
        rechargeService.recharge(new CreateRechargeCommand(userId, 200L, "conflicting-key"));

        assertThatThrownBy(() -> rechargeService.recharge(
                new CreateRechargeCommand(userId, 300L, "conflicting-key")
        )).isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(WalletErrorCode.REQUEST_REPLAY_CONFLICT));

        assertThat(accountService.balanceOfUser(userId)).isEqualTo(200L);
        assertThat(quotaPort.usage(userId).grantedAmount()).isEqualTo(200L);
    }

    @Test
    void sameGrantKeyShouldRemainScopedPerUser() {
        UUID firstUserId = uuid(101);
        UUID secondUserId = uuid(202);

        RechargeOrderResult first = rechargeService.recharge(
                new CreateRechargeCommand(firstUserId, 200L, "shared-users-key")
        );
        RechargeOrderResult second = rechargeService.recharge(
                new CreateRechargeCommand(secondUserId, 300L, "shared-users-key")
        );

        assertThat(second.orderId()).isNotEqualTo(first.orderId());
        assertThat(accountService.balanceOfUser(firstUserId)).isEqualTo(200L);
        assertThat(accountService.balanceOfUser(secondUserId)).isEqualTo(300L);
        assertThat(countRows("recharge_order")).isEqualTo(2);
    }

    @Test
    void discardReplayShouldNotDebitTheWalletOrQuotaTwice() {
        UUID userId = uuid(101);
        rechargeService.recharge(new CreateRechargeCommand(userId, 600L, "grant-before-replay"));
        CreateWithdrawCommand command = new CreateWithdrawCommand(userId, 250L, "discard-replay");

        WithdrawOrderResult first = withdrawService.withdraw(command);
        WithdrawOrderResult second = withdrawService.withdraw(command);

        assertThat(second.orderId()).isEqualTo(first.orderId());
        assertThat(accountService.balanceOfUser(userId)).isEqualTo(350L);
        assertThat(quotaPort.usage(userId).discardedAmount()).isEqualTo(250L);
        assertThat(countRows("withdraw_order")).isEqualTo(1);
        assertThat(countRows("wallet_txn")).isEqualTo(2);
    }

    @Test
    void grantLedgerFailureShouldRollBackQuotaIdempotencyOrderLedgerAndAccounts() {
        UUID userId = uuid(303);
        doThrow(new IllegalStateException("ledger post failed"))
                .when(ledgerService).post(any());

        assertThatThrownBy(() -> rechargeService.recharge(
                new CreateRechargeCommand(userId, 400L, "grant-ledger-failure")
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ledger post failed");

        assertThat(quotaPort.usage(userId)).isEqualTo(WalletTestCreditQuotaPort.Usage.empty());
        assertThat(countRows("http_idempotency")).isZero();
        assertThat(countRows("recharge_order")).isZero();
        assertThat(countRows("wallet_txn")).isZero();
        assertThat(countRows("wallet_entry")).isZero();
        assertThat(countRows("wallet_account")).isZero();
    }

    @Test
    void discardLedgerFailureShouldRollBackItsQuotaIdempotencyOrderLedgerAndAccountChanges() {
        UUID userId = uuid(404);
        rechargeService.recharge(new CreateRechargeCommand(userId, 600L, "discard-rollback-grant"));
        WalletTestCreditQuotaPort.Usage usageBefore = quotaPort.usage(userId);
        int idempotencyRowsBefore = countRows("http_idempotency");
        int rechargeRowsBefore = countRows("recharge_order");
        int txnRowsBefore = countRows("wallet_txn");
        int entryRowsBefore = countRows("wallet_entry");
        int accountRowsBefore = countRows("wallet_account");
        long userBalanceBefore = accountService.balanceOfUser(userId);
        long expenseBalanceBefore = accountService.balanceOfSystem("PLATFORM_TEST_CREDIT_EXPENSE");
        reset(ledgerService);
        doThrow(new IllegalStateException("ledger post failed"))
                .when(ledgerService).post(any());

        assertThatThrownBy(() -> withdrawService.withdraw(
                new CreateWithdrawCommand(userId, 250L, "discard-ledger-failure")
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ledger post failed");

        assertThat(quotaPort.usage(userId)).isEqualTo(usageBefore);
        assertThat(countRows("http_idempotency")).isEqualTo(idempotencyRowsBefore);
        assertThat(countRows("recharge_order")).isEqualTo(rechargeRowsBefore);
        assertThat(countRows("withdraw_order")).isZero();
        assertThat(countRows("wallet_txn")).isEqualTo(txnRowsBefore);
        assertThat(countRows("wallet_entry")).isEqualTo(entryRowsBefore);
        assertThat(countRows("wallet_account")).isEqualTo(accountRowsBefore);
        assertThat(accountService.balanceOfUser(userId)).isEqualTo(userBalanceBefore);
        assertThat(accountService.balanceOfSystem("PLATFORM_TEST_CREDIT_EXPENSE"))
                .isEqualTo(expenseBalanceBefore);
    }

    private String txnTypeFor(String requestIdPattern) {
        return jdbcTemplate.queryForObject(
                "select txn_type from wallet_txn where request_id like ?",
                String.class,
                requestIdPattern
        );
    }

    private int countRows(String tableName) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from " + tableName, Integer.class);
        return count == null ? 0 : count;
    }
}
