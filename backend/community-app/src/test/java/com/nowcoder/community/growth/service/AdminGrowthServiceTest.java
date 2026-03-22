package com.nowcoder.community.growth.service;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.growth.dto.AdminAdjustBalanceRequest;
import com.nowcoder.community.growth.dto.AdminGrowthUserResponse;
import com.nowcoder.community.growth.exception.GrowthErrorCode;
import com.nowcoder.community.infra.web.net.ClientIpResolver;
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
class AdminGrowthServiceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AdminGrowthService service;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from admin_reward_adjustment");
        jdbcTemplate.update("delete from reward_ledger");
        jdbcTemplate.update("delete from reward_account");
        jdbcTemplate.update("delete from user_score_log");
        jdbcTemplate.update("update user set score = 0");
    }

    @Test
    void searchShouldReturnCurrentScoreLevelBalancesAndRecentLedgerSummary() {
        jdbcTemplate.update("update user set score = 320 where id = 1");
        jdbcTemplate.update(
                "insert into reward_account(user_id, available_balance, frozen_balance, version, update_time) values (1, 15, 4, 0, current_timestamp)"
        );
        jdbcTemplate.update(
                "insert into reward_ledger(user_id, event_id, event_type, delta, balance_after, source_module, remark, create_time) values (1, 'evt-1', 'TaskReward', 5, 10, 'growth', 'daily', current_timestamp)"
        );
        jdbcTemplate.update(
                "insert into reward_ledger(user_id, event_id, event_type, delta, balance_after, source_module, remark, create_time) values (1, 'evt-2', 'RewardRedeemed', -3, 7, 'growth', 'shop', current_timestamp)"
        );

        AdminGrowthUserResponse response = service.search(1, null, null);

        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo(1);
        assertThat(response.getScore()).isEqualTo(320);
        assertThat(response.getLevel()).isEqualTo(4);
        assertThat(response.getRewardBalance()).isEqualTo(15);
        assertThat(response.getFrozenBalance()).isEqualTo(4);
        assertThat(response.getRecentRewardLedgers()).hasSize(2);
    }

    @Test
    void adjustShouldRequireReasonAndConfirmation() {
        AdminAdjustBalanceRequest request = new AdminAdjustBalanceRequest();
        request.setTargetUserId(1);
        request.setAssetType("REWARD_BALANCE");
        request.setDelta(5);
        request.setConfirm(false);
        request.setReason("   ");

        assertThatThrownBy(() -> service.adjust(99, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(GrowthErrorCode.INVALID_REQUEST))
                .hasMessageContaining("confirm");
    }

    @Test
    void rewardBalanceAdjustmentShouldWriteOneAuditRecordWithBeforeAndAfterValues() {
        jdbcTemplate.update(
                "insert into reward_account(user_id, available_balance, frozen_balance, version, update_time) values (1, 10, 0, 0, current_timestamp)"
        );

        AdminAdjustBalanceRequest request = new AdminAdjustBalanceRequest();
        request.setTargetUserId(1);
        request.setAssetType("REWARD_BALANCE");
        request.setDelta(5);
        request.setConfirm(true);
        request.setReason("manual compensation");

        AdminGrowthUserResponse response = service.adjust(99, request);

        assertThat(response.getRewardBalance()).isEqualTo(15);
        assertThat(jdbcTemplate.queryForObject("select available_balance from reward_account where user_id = 1", Integer.class)).isEqualTo(15);
        assertThat(jdbcTemplate.queryForObject("select count(*) from admin_reward_adjustment", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select before_value from admin_reward_adjustment", Integer.class)).isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject("select after_value from admin_reward_adjustment", Integer.class)).isEqualTo(15);
        assertThat(jdbcTemplate.queryForObject("select actor_user_id from admin_reward_adjustment", Integer.class)).isEqualTo(99);
    }
}
