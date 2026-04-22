package com.nowcoder.community.growth.service;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class UnifiedGrantServiceTest {

    private static final UUID USER_ID = uuid(1);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UnifiedGrantService service;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from reward_ledger");
        jdbcTemplate.update("delete from reward_grant_record");
        jdbcTemplate.update("delete from reward_account");
        jdbcTemplate.update("delete from user_score_log");
        jdbcTemplate.update("delete from wallet_entry");
        jdbcTemplate.update("delete from wallet_txn");
        jdbcTemplate.update("delete from wallet_account");
        jdbcTemplate.update(
                "merge into user (id, username, password, salt, email, type, status, header_url, create_time, score) key(id) " +
                        "values (?, ?, ?, ?, ?, ?, ?, ?, current_timestamp, 0)",
                BinaryUuidCodec.toBytes(USER_ID),
                "u1",
                "p",
                "s",
                "u1@example.com",
                0,
                1,
                "http://old.local/a.png"
        );
        jdbcTemplate.update("update user set score = 0");
    }

    @Test
    void pointsProjectionActionShouldOwnGrantBookkeepingAndRemainIdempotent() {
        boolean first = service.applyPointsProjection(USER_ID, "post-evt-1", "PostPublished", 10);
        boolean second = service.applyPointsProjection(USER_ID, "post-evt-1", "PostPublished", 10);

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(currentScore(USER_ID)).isZero();
        assertThat(currentRewardBalance(USER_ID)).isZero();
        assertThat(currentWalletBalance(USER_ID)).isEqualTo(10);
        assertThat(countRows("reward_grant_record")).isEqualTo(1);
        assertThat(countRows("user_score_log")).isZero();
        assertThat(countRows("reward_ledger")).isZero();
        assertThat(countRows("wallet_txn")).isEqualTo(1);
        assertThat(countRows("wallet_entry")).isEqualTo(2);
        assertThat(storedGrantId("post-evt-1")).isEqualTo("post-evt-1:points");
    }

    @Test
    void sameGrantShouldOnlyApplyOnce() {
        boolean first = service.applyGrant(USER_ID, "grant-1", "DailyTask", "src-1", "TaskCompleted", 10, 5, "growth", "daily reward");
        boolean second = service.applyGrant(USER_ID, "grant-1", "DailyTask", "src-1", "TaskCompleted", 10, 5, "growth", "daily reward");

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(currentScore(USER_ID)).isZero();
        assertThat(currentRewardBalance(USER_ID)).isZero();
        assertThat(currentWalletBalance(USER_ID)).isEqualTo(15);
        assertThat(countRows("reward_grant_record")).isEqualTo(1);
        assertThat(countRows("user_score_log")).isZero();
        assertThat(countRows("reward_ledger")).isZero();
        assertThat(countRows("wallet_txn")).isEqualTo(1);
        assertThat(countRows("wallet_entry")).isEqualTo(2);
    }

    @Test
    void growthOnlyGrantShouldCreditWalletWithoutLegacyScoreSideEffects() {
        boolean applied = service.applyGrant(USER_ID, "grant-growth", "PostPublished", "post-evt-1", "PostPublished", 10, 0, "growth", "post reward");

        assertThat(applied).isTrue();
        assertThat(currentScore(USER_ID)).isZero();
        assertThat(currentRewardBalance(USER_ID)).isZero();
        assertThat(currentWalletBalance(USER_ID)).isEqualTo(10);
        assertThat(countRows("user_score_log")).isZero();
        assertThat(countRows("reward_ledger")).isZero();
        assertThat(countRows("wallet_txn")).isEqualTo(1);
        assertThat(countRows("wallet_entry")).isEqualTo(2);
    }

    @Test
    void rewardOnlyGrantShouldCreditWalletWithoutLegacyRewardLedger() {
        boolean applied = service.applyGrant(USER_ID, "grant-reward", "CheckInReward", "checkin-evt-1", "CheckInCompleted", 0, 12, "growth", "check-in reward");

        assertThat(applied).isTrue();
        assertThat(currentScore(USER_ID)).isZero();
        assertThat(currentRewardBalance(USER_ID)).isZero();
        assertThat(currentWalletBalance(USER_ID)).isEqualTo(12);
        assertThat(countRows("user_score_log")).isZero();
        assertThat(countRows("reward_ledger")).isZero();
        assertThat(countRows("wallet_txn")).isEqualTo(1);
        assertThat(countRows("wallet_entry")).isEqualTo(2);
    }

    @Test
    void dualGrantShouldCoalesceIntoSingleWalletPosting() {
        boolean applied = service.applyGrant(USER_ID, "grant-dual", "DailyTask", "task-evt-1", "TaskCompleted", 10, 8, "growth", "daily task reward");

        assertThat(applied).isTrue();
        assertThat(currentScore(USER_ID)).isZero();
        assertThat(currentRewardBalance(USER_ID)).isZero();
        assertThat(currentWalletBalance(USER_ID)).isEqualTo(18);
        assertThat(countRows("reward_grant_record")).isEqualTo(1);
        assertThat(countRows("user_score_log")).isZero();
        assertThat(countRows("reward_ledger")).isZero();
        assertThat(countRows("wallet_txn")).isEqualTo(1);
        assertThat(countRows("wallet_entry")).isEqualTo(2);
    }

    @Test
    void negativeRewardGrantShouldRejectBeforeWalletPostingWhenBalanceIsInsufficient() {
        assertThatThrownBy(() -> service.applyGrant(USER_ID, "grant-debit", "RedeemReward", "redeem-evt-1", "RewardRedeemed", 0, -5, "shop", "redeem"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(WalletErrorCode.ACCOUNT_BALANCE_INSUFFICIENT));

        assertThat(currentScore(USER_ID)).isZero();
        assertThat(currentRewardBalance(USER_ID)).isZero();
        assertThat(currentWalletBalance(USER_ID)).isZero();
        assertThat(countRows("reward_grant_record")).isZero();
        assertThat(countRows("reward_ledger")).isZero();
        assertThat(countRows("wallet_txn")).isZero();
        assertThat(countRows("wallet_entry")).isZero();
    }

    private int currentScore(UUID userId) {
        Integer score = jdbcTemplate.queryForObject(
                "select score from user where id = ?",
                Integer.class,
                BinaryUuidCodec.toBytes(userId)
        );
        return score == null ? 0 : score;
    }

    private int currentRewardBalance(UUID userId) {
        var rows = jdbcTemplate.queryForList(
                "select available_balance from reward_account where user_id = ?",
                BinaryUuidCodec.toBytes(userId)
        );
        if (rows.isEmpty()) {
            return 0;
        }
        return ((Number) rows.get(0).get("AVAILABLE_BALANCE")).intValue();
    }

    private long currentWalletBalance(UUID userId) {
        var rows = jdbcTemplate.queryForList(
                "select balance from wallet_account where owner_type = 'USER' and owner_id = ? and account_type = 'USER_WALLET'",
                BinaryUuidCodec.toBytes(userId)
        );
        if (rows.isEmpty()) {
            return 0L;
        }
        return ((Number) rows.get(0).get("BALANCE")).longValue();
    }

    private int countRows(String tableName) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from " + tableName, Integer.class);
        return count == null ? 0 : count;
    }

    private String storedGrantId(String sourceEventId) {
        return jdbcTemplate.queryForObject(
                "select grant_id from reward_grant_record where source_event_id = ?",
                String.class,
                sourceEventId
        );
    }
}
