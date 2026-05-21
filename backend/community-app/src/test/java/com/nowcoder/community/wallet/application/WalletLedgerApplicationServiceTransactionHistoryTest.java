package com.nowcoder.community.wallet.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.wallet.application.command.ListWalletTransactionsCommand;
import com.nowcoder.community.wallet.application.result.WalletTransactionResult;
import com.nowcoder.community.wallet.domain.model.WalletPosting;
import com.nowcoder.community.wallet.domain.model.WalletTxnType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class WalletLedgerApplicationServiceTransactionHistoryTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WalletLedgerApplicationService ledgerService;

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
    void recentTransactionsShouldReturnEmptyWithoutCreatingWalletAccount() {
        UUID userId = uuid(901);

        List<WalletTransactionResult> rows = ledgerService.recentTransactions(new ListWalletTransactionsCommand(userId, 12));

        assertThat(rows).isEmpty();
        assertThat(countRows("wallet_account")).isZero();
    }

    @Test
    void recentTransactionsShouldReturnSignedTransferRowsForSenderAndReceiver() {
        UUID senderUserId = uuid(101);
        UUID receiverUserId = uuid(202);
        UUID senderAccountId = accountService.ensureUserWallet(senderUserId);
        accountService.ensureUserWallet(receiverUserId);
        seedBalance(senderAccountId, 900L);

        ledgerService.post(
                "wallet:transfer:history",
                WalletTxnType.TRANSFER,
                List.of(
                        WalletPosting.debit(accountService.ensureUserWallet(senderUserId), 300),
                        WalletPosting.credit(accountService.ensureUserWallet(receiverUserId), 300)
                )
        );

        List<WalletTransactionResult> senderRows = ledgerService.recentTransactions(new ListWalletTransactionsCommand(senderUserId, 12));
        List<WalletTransactionResult> receiverRows = ledgerService.recentTransactions(new ListWalletTransactionsCommand(receiverUserId, 12));

        assertThat(senderRows).hasSize(1);
        assertThat(senderRows.get(0).txnRef()).isEqualTo("wallet:transfer:history");
        assertThat(senderRows.get(0).txnType()).isEqualTo("TRANSFER");
        assertThat(senderRows.get(0).amount()).isEqualTo(-300L);
        assertThat(senderRows.get(0).balanceAfter()).isEqualTo(600L);
        assertThat(senderRows.get(0).counterpartLabel()).isEqualTo("用户 " + receiverUserId);

        assertThat(receiverRows).hasSize(1);
        assertThat(receiverRows.get(0).amount()).isEqualTo(300L);
        assertThat(receiverRows.get(0).balanceAfter()).isEqualTo(300L);
        assertThat(receiverRows.get(0).counterpartLabel()).isEqualTo("用户 " + senderUserId);
    }

    @Test
    void recentTransactionsShouldReturnOnlyUserFacingWithdrawalEntry() {
        UUID userId = uuid(101);
        UUID userAccountId = accountService.ensureUserWallet(userId);
        UUID pendingAccountId = accountService.ensureSystemAccount("WITHDRAW_PENDING");
        UUID platformCashAccountId = accountService.ensureSystemAccount("PLATFORM_CASH");
        seedBalance(userAccountId, 500L);
        seedBalance(platformCashAccountId, 500L);

        ledgerService.post(
                "wallet:withdraw:history:request",
                WalletTxnType.WITHDRAW,
                "withdraw-order-1",
                List.of(
                        WalletPosting.debit(userAccountId, 200),
                        WalletPosting.credit(pendingAccountId, 200)
                )
        );
        ledgerService.post(
                "wallet:withdraw:history:settle",
                WalletTxnType.WITHDRAW,
                "withdraw-order-1",
                List.of(
                        WalletPosting.debit(pendingAccountId, 200),
                        WalletPosting.credit(platformCashAccountId, 200)
                )
        );

        List<WalletTransactionResult> rows = ledgerService.recentTransactions(new ListWalletTransactionsCommand(userId, 12));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).txnRef()).isEqualTo("wallet:withdraw:history:request");
        assertThat(rows.get(0).amount()).isEqualTo(-200L);
        assertThat(rows.get(0).counterpartLabel()).isEqualTo("提现申请");
    }

    @Test
    void recentTransactionsShouldClampLimitAndReturnNewestFirst() {
        UUID userId = uuid(101);
        UUID userAccountId = accountService.ensureUserWallet(userId);
        UUID rewardAccountId = accountService.ensureSystemAccount("PLATFORM_REWARD_EXPENSE");

        for (int i = 1; i <= 55; i++) {
            ledgerService.post(
                    "wallet:reward:history:" + i,
                    WalletTxnType.REWARD_ISSUE,
                    List.of(
                            WalletPosting.debit(rewardAccountId, 1),
                            WalletPosting.credit(userAccountId, 1)
                    )
            );
        }

        List<WalletTransactionResult> rows = ledgerService.recentTransactions(new ListWalletTransactionsCommand(userId, 999));

        assertThat(rows).hasSize(50);
        assertThat(rows.get(0).txnRef()).isEqualTo("wallet:reward:history:55");
        assertThat(rows.get(49).txnRef()).isEqualTo("wallet:reward:history:6");
    }

    private void seedBalance(UUID accountId, long balance) {
        jdbcTemplate.update("update wallet_account set balance = ?, version = 0 where account_id = ?", balance, accountId);
    }

    private int countRows(String tableName) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from " + tableName, Integer.class);
        return count == null ? 0 : count;
    }
}
