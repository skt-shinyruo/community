package com.nowcoder.community.growth.service;

import com.nowcoder.community.app.CommunityAppApplication;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class UnifiedGrantServiceTest {

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
        jdbcTemplate.update("update user set score = 0");
    }

    @Test
    void pointsProjectionActionShouldOwnGrantBookkeepingAndRemainIdempotent() {
        boolean first = service.applyPointsProjection(1, "post-evt-1", "PostPublished", 10);
        boolean second = service.applyPointsProjection(1, "post-evt-1", "PostPublished", 10);

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(currentScore(1)).isZero();
        assertThat(currentRewardBalance(1)).isZero();
        assertThat(currentWalletBalance(1)).isEqualTo(10);
        assertThat(countRows("reward_grant_record")).isEqualTo(1);
        assertThat(countRows("user_score_log")).isZero();
        assertThat(countRows("reward_ledger")).isZero();
        assertThat(countRows("wallet_txn")).isEqualTo(1);
        assertThat(countRows("wallet_entry")).isEqualTo(2);
        assertThat(storedGrantId("post-evt-1")).isEqualTo("post-evt-1:points");
    }

    @Test
    void sameGrantShouldOnlyApplyOnce() {
        boolean first = service.applyGrant(1, "grant-1", "DailyTask", "src-1", "TaskCompleted", 10, 5, "growth", "daily reward");
        boolean second = service.applyGrant(1, "grant-1", "DailyTask", "src-1", "TaskCompleted", 10, 5, "growth", "daily reward");

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(currentScore(1)).isZero();
        assertThat(currentRewardBalance(1)).isZero();
        assertThat(currentWalletBalance(1)).isEqualTo(15);
        assertThat(countRows("reward_grant_record")).isEqualTo(1);
        assertThat(countRows("user_score_log")).isZero();
        assertThat(countRows("reward_ledger")).isZero();
        assertThat(countRows("wallet_txn")).isEqualTo(1);
        assertThat(countRows("wallet_entry")).isEqualTo(2);
    }

    @Test
    void growthOnlyGrantShouldCreditWalletWithoutLegacyScoreSideEffects() {
        boolean applied = service.applyGrant(1, "grant-growth", "PostPublished", "post-evt-1", "PostPublished", 10, 0, "growth", "post reward");

        assertThat(applied).isTrue();
        assertThat(currentScore(1)).isZero();
        assertThat(currentRewardBalance(1)).isZero();
        assertThat(currentWalletBalance(1)).isEqualTo(10);
        assertThat(countRows("user_score_log")).isZero();
        assertThat(countRows("reward_ledger")).isZero();
        assertThat(countRows("wallet_txn")).isEqualTo(1);
        assertThat(countRows("wallet_entry")).isEqualTo(2);
    }

    @Test
    void rewardOnlyGrantShouldCreditWalletWithoutLegacyRewardLedger() {
        boolean applied = service.applyGrant(1, "grant-reward", "CheckInReward", "checkin-evt-1", "CheckInCompleted", 0, 12, "growth", "check-in reward");

        assertThat(applied).isTrue();
        assertThat(currentScore(1)).isZero();
        assertThat(currentRewardBalance(1)).isZero();
        assertThat(currentWalletBalance(1)).isEqualTo(12);
        assertThat(countRows("user_score_log")).isZero();
        assertThat(countRows("reward_ledger")).isZero();
        assertThat(countRows("wallet_txn")).isEqualTo(1);
        assertThat(countRows("wallet_entry")).isEqualTo(2);
    }

    @Test
    void dualGrantShouldCoalesceIntoSingleWalletPosting() {
        boolean applied = service.applyGrant(1, "grant-dual", "DailyTask", "task-evt-1", "TaskCompleted", 10, 8, "growth", "daily task reward");

        assertThat(applied).isTrue();
        assertThat(currentScore(1)).isZero();
        assertThat(currentRewardBalance(1)).isZero();
        assertThat(currentWalletBalance(1)).isEqualTo(18);
        assertThat(countRows("reward_grant_record")).isEqualTo(1);
        assertThat(countRows("user_score_log")).isZero();
        assertThat(countRows("reward_ledger")).isZero();
        assertThat(countRows("wallet_txn")).isEqualTo(1);
        assertThat(countRows("wallet_entry")).isEqualTo(2);
    }

    @Test
    void negativeRewardGrantShouldRejectBeforeWalletPostingWhenBalanceIsInsufficient() {
        assertThatThrownBy(() -> service.applyGrant(1, "grant-debit", "RedeemReward", "redeem-evt-1", "RewardRedeemed", 0, -5, "shop", "redeem"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(WalletErrorCode.ACCOUNT_BALANCE_INSUFFICIENT));

        assertThat(currentScore(1)).isZero();
        assertThat(currentRewardBalance(1)).isZero();
        assertThat(currentWalletBalance(1)).isZero();
        assertThat(countRows("reward_grant_record")).isZero();
        assertThat(countRows("reward_ledger")).isZero();
        assertThat(countRows("wallet_txn")).isZero();
        assertThat(countRows("wallet_entry")).isZero();
    }

    private int currentScore(int userId) {
        Integer score = jdbcTemplate.queryForObject("select score from user where id = ?", Integer.class, userId);
        return score == null ? 0 : score;
    }

    private int currentRewardBalance(int userId) {
        var rows = jdbcTemplate.queryForList(
                "select available_balance from reward_account where user_id = ?",
                userId
        );
        if (rows.isEmpty()) {
            return 0;
        }
        return ((Number) rows.get(0).get("AVAILABLE_BALANCE")).intValue();
    }

    private long currentWalletBalance(int userId) {
        var rows = jdbcTemplate.queryForList(
                "select balance from wallet_account where owner_type = 'USER' and owner_id = ? and account_type = 'USER_WALLET'",
                userId
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
