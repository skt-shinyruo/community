package com.nowcoder.community.growth.service;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class RewardAccountServiceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RewardAccountService service;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from reward_ledger");
        jdbcTemplate.update("delete from reward_account");
    }

    @Test
    void missingAccountShouldReadAsZeroAndBeCreatedOnFirstCredit() {
        UUID userId = uuid(1);
        assertThat(service.availableBalanceOf(userId)).isZero();
        assertThat(service.frozenBalanceOf(userId)).isZero();
        assertThat(accountRowCount(userId)).isZero();

        service.creditAvailable(userId, "reward-evt-1", "TaskReward", 10, "growth", "first credit");

        assertThat(accountRowCount(userId)).isEqualTo(1);
        assertThat(service.availableBalanceOf(userId)).isEqualTo(10);
        assertThat(service.frozenBalanceOf(userId)).isZero();
    }

    @Test
    void repeatedCreditsShouldReuseTheSameAccountRow() {
        UUID userId = uuid(1);
        service.creditAvailable(userId, "reward-evt-1", "TaskReward", 10, "growth", "first credit");
        service.creditAvailable(userId, "reward-evt-2", "TaskReward", 5, "growth", "second credit");

        assertThat(accountRowCount(userId)).isEqualTo(1);
        assertThat(service.availableBalanceOf(userId)).isEqualTo(15);
    }

    @Test
    void creditShouldAppendRewardLedgerWithBalanceAfter() {
        UUID userId = uuid(1);
        service.creditAvailable(userId, "reward-evt-1", "TaskReward", 10, "growth", "first credit");
        service.creditAvailable(userId, "reward-evt-2", "TaskReward", 5, "growth", "second credit");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select event_id, event_type, delta, balance_after from reward_ledger where user_id = ? order by id asc",
                BinaryUuidCodec.toBytes(userId)
        );

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("EVENT_ID", "reward-evt-1");
        assertThat(rows.get(0)).containsEntry("EVENT_TYPE", "TaskReward");
        assertThat(((Number) rows.get(0).get("DELTA")).intValue()).isEqualTo(10);
        assertThat(((Number) rows.get(0).get("BALANCE_AFTER")).intValue()).isEqualTo(10);
        assertThat(rows.get(1)).containsEntry("EVENT_ID", "reward-evt-2");
        assertThat(rows.get(1)).containsEntry("EVENT_TYPE", "TaskReward");
        assertThat(((Number) rows.get(1).get("DELTA")).intValue()).isEqualTo(5);
        assertThat(((Number) rows.get(1).get("BALANCE_AFTER")).intValue()).isEqualTo(15);
    }

    @Test
    void deductFrozenBalanceShouldAppendLedgerWithUpdatedFrozenBalance() {
        UUID userId = uuid(1);
        jdbcTemplate.update(
                "insert into reward_account(user_id, available_balance, frozen_balance, version, update_time) values (?, 10, 6, 0, current_timestamp)",
                BinaryUuidCodec.toBytes(userId)
        );

        service.deductFrozenBalance(userId, "reward-evt-3", "RewardOrderFulfilled", 4, "growth", "fulfill");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select event_id, event_type, delta, balance_after, frozen_balance_after from reward_ledger where user_id = ?",
                BinaryUuidCodec.toBytes(userId)
        );

        assertThat(row).containsEntry("EVENT_ID", "reward-evt-3");
        assertThat(row).containsEntry("EVENT_TYPE", "RewardOrderFulfilled");
        assertThat(((Number) row.get("DELTA")).intValue()).isZero();
        assertThat(((Number) row.get("BALANCE_AFTER")).intValue()).isEqualTo(10);
        assertThat(((Number) row.get("FROZEN_BALANCE_AFTER")).intValue()).isEqualTo(2);
        assertThat(service.availableBalanceOf(userId)).isEqualTo(10);
        assertThat(service.frozenBalanceOf(userId)).isEqualTo(2);
    }

    private int accountRowCount(UUID userId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from reward_account where user_id = ?",
                Integer.class,
                BinaryUuidCodec.toBytes(userId)
        );
        return count == null ? 0 : count;
    }
}
