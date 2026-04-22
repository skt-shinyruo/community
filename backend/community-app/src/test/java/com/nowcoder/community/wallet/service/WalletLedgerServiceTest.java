package com.nowcoder.community.wallet.service;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
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
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
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
        UUID userId = uuid(101);
        UUID userAccountId = service.ensureUserWallet(userId);
        UUID systemAccountId = service.ensureSystemAccount("PLATFORM_REWARD_EXPENSE");

        WalletTxnResult result = service.post(
                "reward:101:2026-04-02",
                WalletTxnType.REWARD_ISSUE,
                List.of(
                        WalletPosting.debit(systemAccountId, 500),
                        WalletPosting.credit(userAccountId, 500)
                )
        );

        assertThat(result.txnId()).isNotNull();
        assertThat(result.txnId().version()).isEqualTo(7);
        assertThat(service.balanceOfUser(userId)).isEqualTo(500);
        assertThat(service.entriesOfTxn(result.txnId())).hasSize(2);
    }

    @Test
    void postShouldBeIdempotentByRequestId() {
        UUID userId = uuid(101);
        UUID userAccountId = service.ensureUserWallet(userId);
        UUID systemAccountId = service.ensureSystemAccount("PLATFORM_REWARD_EXPENSE");

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
        assertThat(service.balanceOfUser(userId)).isEqualTo(500);
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
        UUID userId = uuid(101);
        UUID userAccountId = service.ensureUserWallet(userId);
        UUID pendingAccountId = service.ensureSystemAccount("WITHDRAW_PENDING");

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
        UUID userId = uuid(101);
        jdbcTemplate.update(
                "insert into reward_account(user_id, available_balance, frozen_balance, version) values (?,?,?,?)",
                BinaryUuidCodec.toBytes(userId),
                880,
                0,
                0
        );

        migrationService.migrateUser(userId);

        assertThat(accountService.balanceOfUser(userId)).isEqualTo(880);
        assertThat(txnMapper.selectByRequestId("migration:opening:" + userId).getTxnType()).isEqualTo("OPENING_BALANCE");
    }

    @Test
    void migrateUserShouldCarryFrozenOnlyLegacyBalanceIntoMigrationHold() {
        UUID userId = uuid(101);
        jdbcTemplate.update(
                "insert into reward_account(user_id, available_balance, frozen_balance, version) values (?,?,?,?)",
                BinaryUuidCodec.toBytes(userId),
                0,
                66,
                0
        );

        migrationService.migrateUser(userId);

        assertThat(accountService.balanceOfUser(userId)).isZero();
        assertThat(systemBalance("MIGRATION_HOLD")).isEqualTo(66);
        assertThat(systemBalance("RISK_FROZEN")).isEqualTo(66);
        assertThat(txnMapper.selectByRequestId("migration:frozen:" + userId).getTxnType()).isEqualTo("FREEZE");
        assertThat(txnMapper.selectByRequestId("migration:opening:" + userId)).isNull();
    }

    @Test
    void migrateUserShouldCarryBothAvailableAndFrozenLegacyBalances() {
        UUID userId = uuid(101);
        jdbcTemplate.update(
                "insert into reward_account(user_id, available_balance, frozen_balance, version) values (?,?,?,?)",
                BinaryUuidCodec.toBytes(userId),
                880,
                66,
                0
        );

        migrationService.migrateUser(userId);

        assertThat(accountService.balanceOfUser(userId)).isEqualTo(880);
        assertThat(systemBalance("MIGRATION_HOLD")).isEqualTo(946);
        assertThat(systemBalance("RISK_FROZEN")).isEqualTo(66);
        assertThat(txnMapper.selectByRequestId("migration:opening:" + userId).getTxnType()).isEqualTo("OPENING_BALANCE");
        assertThat(txnMapper.selectByRequestId("migration:frozen:" + userId).getTxnType()).isEqualTo("FREEZE");
    }

    @Test
    void postShouldRejectIfAnyPostingWouldDriveBalanceBelowZero() {
        UUID senderUserId = uuid(101);
        UUID receiverUserId = uuid(202);
        UUID senderAccountId = service.ensureUserWallet(senderUserId);
        UUID receiverAccountId = service.ensureUserWallet(receiverUserId);

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
        UUID userId = uuid(101);
        UUID userAccountId = service.ensureUserWallet(userId);
        UUID pendingAccountId = service.ensureSystemAccount("WITHDRAW_PENDING");
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

    private long systemBalance(String accountType) {
        List<Long> balances = jdbcTemplate.query(
                "select balance from wallet_account where owner_type = 'SYSTEM' and owner_id = ? and account_type = ?",
                (rs, rowNum) -> rs.getLong("balance"),
                BinaryUuidCodec.toBytes(new UUID(0L, 0L)),
                accountType
        );
        Long balance = balances.isEmpty() ? null : balances.get(0);
        return balance == null ? 0L : balance;
    }
}
