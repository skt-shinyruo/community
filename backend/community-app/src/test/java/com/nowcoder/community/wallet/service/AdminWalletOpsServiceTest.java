package com.nowcoder.community.wallet.service;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.wallet.entity.WalletTxn;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import com.nowcoder.community.wallet.mapper.WalletTxnMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class AdminWalletOpsServiceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AdminWalletOpsService adminWalletOpsService;

    @Autowired
    private WalletAccountService accountService;

    @Autowired
    private WalletLedgerService ledgerService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private WithdrawService withdrawService;

    @Autowired
    private RechargeService rechargeService;

    @Autowired
    private WalletTxnMapper walletTxnMapper;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from reward_ledger");
        jdbcTemplate.update("delete from reward_account");
        jdbcTemplate.update("delete from wallet_admin_action");
        jdbcTemplate.update("delete from wallet_entry");
        jdbcTemplate.update("delete from wallet_txn");
        jdbcTemplate.update("delete from recharge_order");
        jdbcTemplate.update("delete from withdraw_order");
        jdbcTemplate.update("delete from transfer_order");
        jdbcTemplate.update("delete from wallet_account");
    }

    @Test
    void freezeShouldBlockTransferAndWithdraw() {
        seedUserBalance(101, 500);
        seedSystemBalance("PLATFORM_CASH", 800);

        adminWalletOpsService.freezeWallet(1, 101, "risk review");

        assertThatThrownBy(() -> transferService.create("transfer:req-2", 101, 202, 100))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(WalletErrorCode.ACCOUNT_FROZEN))
                .hasMessageContaining("frozen");
        assertThatThrownBy(() -> withdrawService.request("withdraw:req-2", 101, 100))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(WalletErrorCode.ACCOUNT_FROZEN))
                .hasMessageContaining("frozen");

        assertThat(countRows("transfer_order")).isZero();
        assertThat(countRows("withdraw_order")).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select status from wallet_account where owner_type = 'USER' and owner_id = ? and account_type = 'USER_WALLET'",
                String.class,
                101
        )).isEqualTo("FROZEN");
        assertThat(countRows("wallet_admin_action")).isEqualTo(1);
    }

    @Test
    void reverseTransferShouldCreateReversalTxnInsteadOfEditingBalancesInPlace() {
        seedUserBalance(101, 900);
        transferService.create("transfer:req-1", 101, 202, 300);

        adminWalletOpsService.reverseTxn(1, "transfer:req-1", "fraud report");

        assertThat(accountService.balanceOfUser(101)).isEqualTo(900);
        assertThat(accountService.balanceOfUser(202)).isEqualTo(0);
        assertThat(countRows("wallet_txn")).isEqualTo(2);
        assertThat(countRows("wallet_entry")).isEqualTo(4);
        assertThat(countRows("wallet_admin_action")).isEqualTo(1);

        WalletTxn original = walletTxnMapper.selectByRequestId("transfer:req-1");
        WalletTxn reversal = walletTxnMapper.selectByRequestId("reversal:transfer:req-1");
        assertThat(original).isNotNull();
        assertThat(reversal).isNotNull();
        assertThat(reversal.getTxnType()).isEqualTo("REVERSAL");
        assertThat(jdbcTemplate.queryForObject("select target_account_id from wallet_admin_action", Long.class))
                .isEqualTo(original.getTxnId());
        assertThat(jdbcTemplate.queryForObject("select request_id from wallet_admin_action", String.class))
                .isEqualTo("wallet-admin:reverse:transfer:req-1");
        assertThat(ledgerService.entriesOfTxn(original.getTxnId()))
                .extracting(entry -> entry.getDirection() + ":" + entry.getAmount())
                .containsExactly("DEBIT:300", "CREDIT:300");
        assertThat(ledgerService.entriesOfTxn(reversal.getTxnId()))
                .extracting(entry -> entry.getDirection() + ":" + entry.getAmount())
                .containsExactly("CREDIT:300", "DEBIT:300");
    }

    @Test
    void reverseShouldRejectWhenRecipientAlreadySpentFunds() {
        seedUserBalance(101, 900);
        transferService.create("transfer:req-spent-origin", 101, 202, 300);
        transferService.create("transfer:req-spent-downstream", 202, 303, 300);

        assertThatThrownBy(() -> adminWalletOpsService.reverseTxn(1, "transfer:req-spent-origin", "fraud report"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(WalletErrorCode.ACCOUNT_BALANCE_INSUFFICIENT))
                .hasMessageContaining("reversal")
                .hasMessageContaining("transfer:req-spent-origin");

        assertThat(accountService.balanceOfUser(101)).isEqualTo(600);
        assertThat(accountService.balanceOfUser(202)).isEqualTo(0);
        assertThat(accountService.balanceOfUser(303)).isEqualTo(300);
        assertThat(countRows("wallet_txn")).isEqualTo(2);
        assertThat(countRows("wallet_entry")).isEqualTo(4);
        assertThat(countRows("wallet_admin_action")).isZero();
    }

    @Test
    void repeatReverseShouldKeepOneLogicalAdminAction() {
        seedUserBalance(101, 900);
        transferService.create("transfer:req-repeat", 101, 202, 300);

        adminWalletOpsService.reverseTxn(1, "transfer:req-repeat", "fraud report");
        adminWalletOpsService.reverseTxn(1, "transfer:req-repeat", "fraud report");

        WalletTxn original = walletTxnMapper.selectByRequestId("transfer:req-repeat");
        assertThat(countRows("wallet_txn")).isEqualTo(2);
        assertThat(countRows("wallet_entry")).isEqualTo(4);
        assertThat(countRows("wallet_admin_action")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select target_account_id from wallet_admin_action", Long.class))
                .isEqualTo(original.getTxnId());
        assertThat(jdbcTemplate.queryForObject("select request_id from wallet_admin_action", String.class))
                .isEqualTo("wallet-admin:reverse:transfer:req-repeat");
    }

    @Test
    void reverseShouldRejectUnsupportedTxnType() {
        seedSystemBalance("PLATFORM_CASH", 900);
        rechargeService.complete("recharge:req-unsupported", 101, 200);

        assertThatThrownBy(() -> adminWalletOpsService.reverseTxn(1, "recharge:req-unsupported", "fraud report"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(WalletErrorCode.INVALID_REQUEST))
                .hasMessageContaining("not reversible");

        assertThat(countRows("wallet_txn")).isEqualTo(1);
        assertThat(countRows("wallet_entry")).isEqualTo(2);
        assertThat(countRows("wallet_admin_action")).isZero();
    }

    private void seedUserBalance(int userId, long balance) {
        long accountId = accountService.ensureUserWallet(userId);
        jdbcTemplate.update(
                "update wallet_account set balance = ?, version = 0, status = 'ACTIVE' where account_id = ?",
                balance,
                accountId
        );
    }

    private void seedSystemBalance(String accountType, long balance) {
        long accountId = accountService.ensureSystemAccount(accountType);
        jdbcTemplate.update(
                "update wallet_account set balance = ?, version = 0, status = 'ACTIVE' where account_id = ?",
                balance,
                accountId
        );
    }

    private int countRows(String tableName) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from " + tableName, Integer.class);
        return count == null ? 0 : count;
    }
}
