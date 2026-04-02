package com.nowcoder.community.wallet.service;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import com.nowcoder.community.wallet.mapper.WalletAccountMapper;
import com.nowcoder.community.wallet.mapper.WalletTxnMapper;
import com.nowcoder.community.wallet.model.WalletPosting;
import com.nowcoder.community.wallet.model.WalletTxnResult;
import com.nowcoder.community.wallet.model.WalletTxnType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class WalletLedgerServiceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WalletLedgerService service;

    @Autowired
    private WalletAccountService accountService;

    @Autowired
    private WalletMigrationService migrationService;

    @Autowired
    private WalletTxnMapper txnMapper;

    @SpyBean
    private WalletAccountMapper walletAccountMapper;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        reset(walletAccountMapper);
        jdbcTemplate.update("delete from reward_ledger");
        jdbcTemplate.update("delete from reward_account");
        jdbcTemplate.update("delete from wallet_entry");
        jdbcTemplate.update("delete from wallet_txn");
        jdbcTemplate.update("delete from wallet_account");
    }

    @Test
    void appendTxnShouldPersistBalancedEntriesAndUpdateBalances() {
        long userAccountId = service.ensureUserWallet(101);
        long systemAccountId = service.ensureSystemAccount("PLATFORM_REWARD_EXPENSE");

        WalletTxnResult result = service.post(
                "reward:101:2026-04-02",
                WalletTxnType.REWARD_ISSUE,
                List.of(
                        WalletPosting.debit(systemAccountId, 500),
                        WalletPosting.credit(userAccountId, 500)
                )
        );

        assertThat(result.txnId()).isPositive();
        assertThat(service.balanceOfUser(101)).isEqualTo(500);
        assertThat(service.entriesOfTxn(result.txnId())).hasSize(2);
    }

    @Test
    void postShouldBeIdempotentByRequestId() {
        long userAccountId = service.ensureUserWallet(101);
        long systemAccountId = service.ensureSystemAccount("PLATFORM_REWARD_EXPENSE");

        WalletTxnResult first = service.post(
                "reward:101:idempotent",
                WalletTxnType.REWARD_ISSUE,
                List.of(
                        WalletPosting.debit(systemAccountId, 500),
                        WalletPosting.credit(userAccountId, 500)
                )
        );
        WalletTxnResult second = service.post(
                "reward:101:idempotent",
                WalletTxnType.REWARD_ISSUE,
                List.of(
                        WalletPosting.debit(systemAccountId, 500),
                        WalletPosting.credit(userAccountId, 500)
                )
        );

        assertThat(second.txnId()).isEqualTo(first.txnId());
        assertThat(second.status()).isEqualTo("SUCCEEDED");
        assertThat(service.balanceOfUser(101)).isEqualTo(500);
        assertThat(txnCount()).isEqualTo(1);
        assertThat(entryCount()).isEqualTo(2);
    }

    @Test
    void ensureSystemAccountShouldRejectUserWalletAccountType() {
        assertThatThrownBy(() -> service.ensureSystemAccount("USER_WALLET"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(WalletErrorCode.INVALID_REQUEST))
                .hasMessageContaining("system accountType");

        assertThat(systemUserWalletCount()).isZero();
    }

    @Test
    void postShouldReportInsufficientBalanceWhenDebitWouldOverdraft() {
        long userAccountId = service.ensureUserWallet(101);
        long pendingAccountId = service.ensureSystemAccount("WITHDRAW_PENDING");

        assertThatThrownBy(() -> service.post(
                "withdraw:101:overdraft",
                WalletTxnType.WITHDRAW,
                List.of(
                        WalletPosting.debit(userAccountId, 100),
                        WalletPosting.credit(pendingAccountId, 100)
                )
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(WalletErrorCode.ACCOUNT_BALANCE_INSUFFICIENT))
                .hasMessageContaining("accountId=" + userAccountId);

        assertThat(txnCount()).isZero();
        assertThat(entryCount()).isZero();
    }

    @Test
    void migrateOpeningBalanceShouldCreateOneOpeningTxnFromLegacyRewardAccount() {
        jdbcTemplate.update(
                "insert into reward_account(user_id, available_balance, frozen_balance, version) values (?,?,?,?)",
                101,
                880,
                0,
                0
        );

        migrationService.migrateUser(101);

        assertThat(accountService.balanceOfUser(101)).isEqualTo(880);
        assertThat(txnMapper.selectByRequestId("migration:opening:101").getTxnType()).isEqualTo("OPENING_BALANCE");
    }

    @Test
    void postShouldRejectIfAnyPostingWouldDriveBalanceBelowZero() {
        long senderAccountId = service.ensureUserWallet(101);
        long receiverAccountId = service.ensureUserWallet(202);

        assertThatThrownBy(() -> service.post(
                "transfer:101:too-much",
                WalletTxnType.TRANSFER,
                List.of(
                        WalletPosting.debit(senderAccountId, 1),
                        WalletPosting.credit(receiverAccountId, 1)
                )
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(WalletErrorCode.ACCOUNT_BALANCE_INSUFFICIENT))
                .hasMessageContaining("accountId=" + senderAccountId);

        assertThat(txnCount()).isZero();
        assertThat(entryCount()).isZero();
    }

    @Test
    void postShouldReportConflictWhenDebitUpdateLosesOptimisticLock() {
        long userAccountId = service.ensureUserWallet(101);
        long pendingAccountId = service.ensureSystemAccount("WITHDRAW_PENDING");
        jdbcTemplate.update(
                "update wallet_account set balance = ?, version = ? where account_id = ?",
                500L,
                7L,
                userAccountId
        );
        doReturn(0).when(walletAccountMapper).updateBalanceWithVersion(userAccountId, 7L, -100L, "ACTIVE");

        assertThatThrownBy(() -> service.post(
                "withdraw:101:conflict",
                WalletTxnType.WITHDRAW,
                List.of(
                        WalletPosting.debit(userAccountId, 100),
                        WalletPosting.credit(pendingAccountId, 100)
                )
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(WalletErrorCode.ACCOUNT_UPDATE_CONFLICT))
                .hasMessageContaining("accountId=" + userAccountId);

        assertThat(txnCount()).isZero();
        assertThat(entryCount()).isZero();
    }

    private int txnCount() {
        Integer count = jdbcTemplate.queryForObject("select count(*) from wallet_txn", Integer.class);
        return count == null ? 0 : count;
    }

    private int entryCount() {
        Integer count = jdbcTemplate.queryForObject("select count(*) from wallet_entry", Integer.class);
        return count == null ? 0 : count;
    }

    private int systemUserWalletCount() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from wallet_account where owner_type = 'SYSTEM' and account_type = 'USER_WALLET'",
                Integer.class
        );
        return count == null ? 0 : count;
    }
}
