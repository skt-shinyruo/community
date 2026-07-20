package com.nowcoder.community.wallet.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import com.nowcoder.community.wallet.infrastructure.persistence.mapper.WalletAccountMapper;
import com.nowcoder.community.wallet.infrastructure.persistence.mapper.WalletTxnMapper;
import com.nowcoder.community.wallet.domain.model.WalletPosting;
import com.nowcoder.community.wallet.application.result.WalletTxnResult;
import com.nowcoder.community.wallet.domain.model.WalletTxnType;
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
class WalletLedgerApplicationServiceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WalletLedgerApplicationService service;

    @SpyBean
    private WalletAccountMapper walletAccountMapper;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        reset(walletAccountMapper);
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
    void recentTransactionsShouldRejectNullCommand() {
        assertThatThrownBy(() -> service.recentTransactions((com.nowcoder.community.wallet.application.command.ListWalletTransactionsCommand) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void postShouldRejectNullCommand() {
        assertThatThrownBy(() -> service.post((com.nowcoder.community.wallet.domain.model.WalletLedgerCommand) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
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
    void postShouldRejectReplayWithDifferentTxnType() {
        UUID userId = uuid(101);
        UUID userAccountId = service.ensureUserWallet(userId);
        UUID systemAccountId = service.ensureSystemAccount("PLATFORM_REWARD_EXPENSE");

        service.post(
                "wallet:replay:type",
                WalletTxnType.REWARD_ISSUE,
                "reward:biz:1",
                List.of(
                        WalletPosting.debit(systemAccountId, 100),
                        WalletPosting.credit(userAccountId, 100)
                )
        );

        assertThatThrownBy(() -> service.post(
                "wallet:replay:type",
                WalletTxnType.TRANSFER,
                "reward:biz:1",
                List.of(
                        WalletPosting.debit(systemAccountId, 100),
                        WalletPosting.credit(userAccountId, 100)
                )
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(WalletErrorCode.REQUEST_REPLAY_CONFLICT))
                .hasMessageContaining("wallet request replay conflict");
    }

    @Test
    void postShouldRejectReplayWithDifferentAmountOrBizId() {
        UUID userId = uuid(101);
        UUID userAccountId = service.ensureUserWallet(userId);
        UUID systemAccountId = service.ensureSystemAccount("PLATFORM_REWARD_EXPENSE");

        service.post(
                "wallet:replay:amount",
                WalletTxnType.REWARD_ISSUE,
                "reward:biz:1",
                List.of(
                        WalletPosting.debit(systemAccountId, 100),
                        WalletPosting.credit(userAccountId, 100)
                )
        );

        assertThatThrownBy(() -> service.post(
                "wallet:replay:amount",
                WalletTxnType.REWARD_ISSUE,
                "reward:biz:2",
                List.of(
                        WalletPosting.debit(systemAccountId, 200),
                        WalletPosting.credit(userAccountId, 200)
                )
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(WalletErrorCode.REQUEST_REPLAY_CONFLICT))
                .hasMessageContaining("wallet request replay conflict");
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
    void privilegedCorrectionShouldAllowDebtAndReuseReplayValidation() {
        UUID userId = uuid(101);
        UUID userAccountId = service.ensureUserWallet(userId);
        UUID systemAccountId = service.ensureSystemAccount("PLATFORM_REWARD_EXPENSE");
        List<WalletPosting> correction = List.of(
                WalletPosting.debit(userAccountId, 5),
                WalletPosting.credit(systemAccountId, 5)
        );

        WalletTxnResult first = service.postPrivilegedCorrection(
                "wallet:correction:debt",
                WalletTxnType.REVERSAL,
                correction
        );
        WalletTxnResult replay = service.postPrivilegedCorrection(
                "wallet:correction:debt",
                WalletTxnType.REVERSAL,
                correction
        );

        assertThat(replay).isEqualTo(first);
        assertThat(service.balanceOfUser(userId)).isEqualTo(-5L);
        assertThat(systemBalance("PLATFORM_REWARD_EXPENSE")).isEqualTo(-5L);
        assertThat(service.entriesOfTxn(first.txnId()))
                .extracting(entry -> entry.getBalanceAfter())
                .containsExactly(-5L, -5L);
        assertThat(txnCount()).isEqualTo(1);
        assertThat(entryCount()).isEqualTo(2);

        assertThatThrownBy(() -> service.postPrivilegedCorrection(
                "wallet:correction:debt",
                WalletTxnType.REVERSAL,
                List.of(
                        WalletPosting.debit(userAccountId, 6),
                        WalletPosting.credit(systemAccountId, 6)
                )
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(WalletErrorCode.REQUEST_REPLAY_CONFLICT));

        assertThatThrownBy(() -> service.post(
                "wallet:normal:debt",
                WalletTxnType.TRANSFER,
                List.of(
                        WalletPosting.debit(userAccountId, 1),
                        WalletPosting.credit(systemAccountId, 1)
                )
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(WalletErrorCode.ACCOUNT_BALANCE_INSUFFICIENT));
        assertThat(txnCount()).isEqualTo(1);
        assertThat(entryCount()).isEqualTo(2);
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
        doReturn(0).when(walletAccountMapper).updateNormalBalanceWithVersion(userAccountId, 7L, -100L, "ACTIVE");

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

    @Test
    void postShouldRejectPostingAmountSumOverflowAsInvalidRequest() {
        UUID firstDebitAccountId = service.ensureSystemAccount("PLATFORM_REWARD_EXPENSE");
        UUID secondDebitAccountId = service.ensureSystemAccount("PLATFORM_CASH");
        UUID firstCreditAccountId = service.ensureUserWallet(uuid(101));
        UUID secondCreditAccountId = service.ensureUserWallet(uuid(202));

        assertThatThrownBy(() -> service.post(
                "wallet:overflow:amount",
                WalletTxnType.REWARD_ISSUE,
                List.of(
                        WalletPosting.debit(firstDebitAccountId, Long.MAX_VALUE),
                        WalletPosting.debit(secondDebitAccountId, Long.MAX_VALUE),
                        WalletPosting.credit(firstCreditAccountId, Long.MAX_VALUE),
                        WalletPosting.credit(secondCreditAccountId, Long.MAX_VALUE)
                )
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(WalletErrorCode.INVALID_REQUEST))
                .hasMessageContaining("amount");

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
