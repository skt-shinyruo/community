package com.nowcoder.community.wallet.infrastructure.persistence;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.wallet.domain.model.WalletAccount;
import com.nowcoder.community.wallet.domain.model.WalletAccountChange;
import com.nowcoder.community.wallet.domain.model.WalletPostingPolicy;
import com.nowcoder.community.wallet.domain.repository.WalletAccountRepository;
import com.nowcoder.community.wallet.domain.repository.WalletAccountRepository.ApplyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class WalletAccountRepositoryApplyTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-7000-8000-000000000711");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-7000-8000-000000000712");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WalletAccountRepository repository;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from wallet_account");
    }

    @Test
    void applyShouldPersistBalanceStatusAndExactlyOneVersionAdvance() {
        seed(500L, "ACTIVE", 7L);
        WalletAccountChange change = account(500L, "ACTIVE", 7L).post(-120L);

        ApplyResult result = repository.apply(change);

        assertThat(result).isEqualTo(ApplyResult.APPLIED);
        assertThat(balance()).isEqualTo(380L);
        assertThat(status()).isEqualTo("ACTIVE");
        assertThat(version()).isEqualTo(8L);
    }

    @Test
    void applyShouldReturnNotFoundWhenTheAccountDisappeared() {
        WalletAccountChange change = account(0L, "ACTIVE", 0L).post(100L);

        ApplyResult result = repository.apply(change);

        assertThat(result).isEqualTo(ApplyResult.NOT_FOUND);
    }

    @Test
    void applyShouldReturnVersionConflictWithoutChangingThePersistedAccount() {
        seed(500L, "ACTIVE", 8L);
        WalletAccountChange stale = account(500L, "ACTIVE", 7L).post(-120L);

        ApplyResult result = repository.apply(stale);

        assertThat(result).isEqualTo(ApplyResult.VERSION_CONFLICT);
        assertThat(balance()).isEqualTo(500L);
        assertThat(version()).isEqualTo(8L);
    }

    @Test
    void applyShouldReturnInsufficientFundsWhenTheDatabaseBalanceConditionRejectsTheChange() {
        seed(100L, "ACTIVE", 7L);
        WalletAccountChange basedOnStaleBalance = account(500L, "ACTIVE", 7L).post(-200L);

        ApplyResult result = repository.apply(basedOnStaleBalance);

        assertThat(result).isEqualTo(ApplyResult.INSUFFICIENT_FUNDS);
        assertThat(balance()).isEqualTo(100L);
        assertThat(version()).isEqualTo(7L);
    }

    @Test
    void privilegedApplyShouldPersistDebtEvenWhenTheDatabaseBalanceIsInsufficient() {
        seed(3L, "FROZEN", 7L);
        WalletAccountChange correction = account(3L, "FROZEN", 7L)
                .post(-5L, WalletPostingPolicy.PRIVILEGED_CORRECTION);

        ApplyResult result = repository.apply(correction);

        assertThat(result).isEqualTo(ApplyResult.APPLIED);
        assertThat(balance()).isEqualTo(-2L);
        assertThat(status()).isEqualTo("FROZEN");
        assertThat(version()).isEqualTo(8L);
    }

    @Test
    void privilegedApplyShouldMapZeroRowsToNotFoundOrVersionConflictOnly() {
        WalletAccountChange missing = account(3L, "ACTIVE", 7L)
                .post(-5L, WalletPostingPolicy.PRIVILEGED_CORRECTION);
        assertThat(repository.apply(missing)).isEqualTo(ApplyResult.NOT_FOUND);

        seed(3L, "ACTIVE", 8L);
        WalletAccountChange stale = account(3L, "ACTIVE", 7L)
                .post(-5L, WalletPostingPolicy.PRIVILEGED_CORRECTION);

        assertThat(repository.apply(stale)).isEqualTo(ApplyResult.VERSION_CONFLICT);
        assertThat(balance()).isEqualTo(3L);
        assertThat(version()).isEqualTo(8L);
    }

    @Test
    void repositoryShouldReconstituteDebtAndLetNormalCreditsRepayIt() {
        seed(-5L, "ACTIVE", 7L);
        WalletAccount debtAccount = repository.findByAccountId(ACCOUNT_ID);
        WalletAccountChange credit = debtAccount.post(3L);

        ApplyResult result = repository.apply(credit);

        assertThat(result).isEqualTo(ApplyResult.APPLIED);
        assertThat(balance()).isEqualTo(-2L);
        assertThat(version()).isEqualTo(8L);
        assertThat(credit.policy()).isEqualTo(WalletPostingPolicy.NORMAL);
    }

    private WalletAccount account(long balance, String status, long version) {
        return WalletAccount.reconstitute(
                ACCOUNT_ID,
                "USER",
                OWNER_ID,
                "USER_WALLET",
                balance,
                status,
                version
        );
    }

    private void seed(long balance, String status, long version) {
        jdbcTemplate.update(
                "insert into wallet_account(account_id, owner_type, owner_id, account_type, balance, status, version) values (?, ?, ?, ?, ?, ?, ?)",
                BinaryUuidCodec.toBytes(ACCOUNT_ID),
                "USER",
                BinaryUuidCodec.toBytes(OWNER_ID),
                "USER_WALLET",
                balance,
                status,
                version
        );
    }

    private long balance() {
        return jdbcTemplate.queryForObject(
                "select balance from wallet_account where account_id = ?",
                Long.class,
                BinaryUuidCodec.toBytes(ACCOUNT_ID)
        );
    }

    private String status() {
        return jdbcTemplate.queryForObject(
                "select status from wallet_account where account_id = ?",
                String.class,
                BinaryUuidCodec.toBytes(ACCOUNT_ID)
        );
    }

    private long version() {
        return jdbcTemplate.queryForObject(
                "select version from wallet_account where account_id = ?",
                Long.class,
                BinaryUuidCodec.toBytes(ACCOUNT_ID)
        );
    }
}
