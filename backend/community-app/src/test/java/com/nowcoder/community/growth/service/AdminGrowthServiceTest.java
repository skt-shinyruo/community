package com.nowcoder.community.growth.service;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.growth.dto.AdminAdjustBalanceRequest;
import com.nowcoder.community.growth.dto.AdminRewardAdjustmentResponse;
import com.nowcoder.community.growth.dto.AdminGrowthUserResponse;
import com.nowcoder.community.growth.dto.RewardLedgerEntryResponse;
import com.nowcoder.community.growth.exception.GrowthErrorCode;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.user.api.action.UserPointsActionApi;
import com.nowcoder.community.user.api.model.UserGrowthProfileView;
import com.nowcoder.community.user.api.model.UserSummaryView;
import com.nowcoder.community.user.api.query.UserLookupQueryApi;
import com.nowcoder.community.user.api.query.UserProfileQueryApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class AdminGrowthServiceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RewardAccountService rewardAccountService;

    @Autowired
    private com.nowcoder.community.growth.mapper.RewardLedgerMapper rewardLedgerMapper;

    @Autowired
    private com.nowcoder.community.growth.mapper.AdminRewardAdjustmentMapper adminRewardAdjustmentMapper;

    @MockBean
    private ClientIpResolver clientIpResolver;

    private UserLookupQueryApi userLookupQueryApi;
    private UserProfileQueryApi userProfileQueryApi;
    private UserPointsActionApi userPointsActionApi;
    private UserLevelService userLevelService;
    private AdminGrowthService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from admin_reward_adjustment");
        jdbcTemplate.update("delete from reward_ledger");
        jdbcTemplate.update("delete from reward_account");
        jdbcTemplate.update("delete from user_score_log");
        jdbcTemplate.update("update user set score = 0");

        userLookupQueryApi = mock(UserLookupQueryApi.class);
        userProfileQueryApi = mock(UserProfileQueryApi.class);
        userPointsActionApi = mock(UserPointsActionApi.class);
        userLevelService = mock(UserLevelService.class);
        when(userLevelService.evaluateLevel(1))
                .thenReturn(new UserLevelService.UserLevelSummary(2, 13, 100, 12, 88, true));
        service = new AdminGrowthService(
                userLookupQueryApi,
                userProfileQueryApi,
                userPointsActionApi,
                rewardAccountService,
                rewardLedgerMapper,
                adminRewardAdjustmentMapper,
                userLevelService
        );
    }

    @Test
    void searchShouldReturnCurrentScoreLevelBalancesAndRecentLedgerSummary() {
        jdbcTemplate.update(
                "insert into reward_account(user_id, available_balance, frozen_balance, version, update_time) values (1, 15, 4, 0, current_timestamp)"
        );
        jdbcTemplate.update(
                "insert into reward_ledger(user_id, event_id, event_type, delta, balance_after, source_module, remark, create_time) values (1, 'evt-1', 'TaskReward', 5, 10, 'growth', 'daily', current_timestamp)"
        );
        jdbcTemplate.update(
                "insert into reward_ledger(user_id, event_id, event_type, delta, balance_after, source_module, remark, create_time) values (1, 'evt-2', 'RewardRedeemed', -3, 7, 'growth', 'shop', current_timestamp)"
        );
        when(userLookupQueryApi.getSummaryById(1))
                .thenReturn(new UserSummaryView(1, "alice", "h1", 0));
        when(userProfileQueryApi.getGrowthProfile(1))
                .thenReturn(new UserGrowthProfileView(1, "alice", 320, 4, "alice@example.com", 1, "h1"));

        AdminGrowthUserResponse response = service.search(1, null, null);

        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo(1);
        assertThat(response.getScore()).isEqualTo(320);
        assertThat(response.getLevel()).isEqualTo(4);
        assertThat(response.getUserLevel()).isEqualTo(2);
        assertThat(response.getSignInDaysInWindow()).isEqualTo(13);
        assertThat(response.getWindowDays()).isEqualTo(100);
        assertThat(response.getRewardBalance()).isEqualTo(15);
        assertThat(response.getFrozenBalance()).isEqualTo(4);
        assertThat(response.getRecentRewardLedgers()).hasSize(2);
        assertThat(response.getRecentRewardLedgers().get(0).getEventType()).isEqualTo("RewardRedeemed");
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
        when(userLookupQueryApi.getSummaryById(1))
                .thenReturn(new UserSummaryView(1, "alice", "h1", 0));
        when(userProfileQueryApi.getGrowthProfile(1))
                .thenReturn(new UserGrowthProfileView(1, "alice", 0, 1, "alice@example.com", 1, "h1"));

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

    @Test
    void scoreAdjustmentShouldDelegateToUserPointsActionApiAndWriteAuditRecord() {
        when(userLookupQueryApi.getSummaryById(1))
                .thenReturn(new UserSummaryView(1, "alice", "h1", 0));
        when(userProfileQueryApi.getGrowthProfile(1)).thenReturn(
                new UserGrowthProfileView(1, "alice", 320, 4, "alice@example.com", 1, "h1"),
                new UserGrowthProfileView(1, "alice", 340, 4, "alice@example.com", 1, "h1")
        );
        when(userPointsActionApi.applyPoints(eq(1), startsWith("admin-adjust:"), eq("AdminGrowthAdjust"), eq(20)))
                .thenReturn(true);

        AdminAdjustBalanceRequest request = new AdminAdjustBalanceRequest();
        request.setTargetUserId(1);
        request.setAssetType("SCORE");
        request.setDelta(20);
        request.setConfirm(true);
        request.setReason("manual score repair");

        AdminGrowthUserResponse response = service.adjust(99, request);

        assertThat(response.getScore()).isEqualTo(340);
        assertThat(jdbcTemplate.queryForObject("select count(*) from admin_reward_adjustment", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select before_value from admin_reward_adjustment", Integer.class)).isEqualTo(320);
        assertThat(jdbcTemplate.queryForObject("select after_value from admin_reward_adjustment", Integer.class)).isEqualTo(340);
        verify(userPointsActionApi).applyPoints(eq(1), startsWith("admin-adjust:"), eq("AdminGrowthAdjust"), eq(20));
    }

    @Test
    void recentRewardLedgerResponsesShouldProjectLedgerDtos() {
        jdbcTemplate.update(
                "insert into reward_ledger(user_id, event_id, event_type, delta, balance_after, frozen_balance_after, biz_key, source_module, remark, create_time) values (1, 'evt-1', 'TaskReward', 5, 10, 2, 'biz-1', 'growth', 'daily', current_timestamp)"
        );

        List<RewardLedgerEntryResponse> ledgers = service.recentRewardLedgerResponses(1, 10);

        assertThat(ledgers).singleElement().satisfies(ledger -> {
            assertThat(ledger.getUserId()).isEqualTo(1);
            assertThat(ledger.getEventId()).isEqualTo("evt-1");
            assertThat(ledger.getEventType()).isEqualTo("TaskReward");
            assertThat(ledger.getDelta()).isEqualTo(5);
            assertThat(ledger.getBalanceAfter()).isEqualTo(10);
            assertThat(ledger.getFrozenBalanceAfter()).isEqualTo(2);
            assertThat(ledger.getBizKey()).isEqualTo("biz-1");
            assertThat(ledger.getSourceModule()).isEqualTo("growth");
            assertThat(ledger.getRemark()).isEqualTo("daily");
        });
    }

    @Test
    void recentAdjustmentResponsesShouldProjectAdjustmentDtos() {
        jdbcTemplate.update(
                "insert into admin_reward_adjustment(actor_user_id, target_user_id, asset_type, delta, before_value, after_value, reason, confirm_token, create_time) values (99, 1, 'REWARD_BALANCE', 5, 10, 15, 'manual compensation', 'confirmed', current_timestamp)"
        );

        List<AdminRewardAdjustmentResponse> adjustments = service.recentAdjustmentResponses(1, 10);

        assertThat(adjustments).singleElement().satisfies(adjustment -> {
            assertThat(adjustment.getActorUserId()).isEqualTo(99);
            assertThat(adjustment.getTargetUserId()).isEqualTo(1);
            assertThat(adjustment.getAssetType()).isEqualTo("REWARD_BALANCE");
            assertThat(adjustment.getDelta()).isEqualTo(5);
            assertThat(adjustment.getBeforeValue()).isEqualTo(10);
            assertThat(adjustment.getAfterValue()).isEqualTo(15);
            assertThat(adjustment.getReason()).isEqualTo("manual compensation");
            assertThat(adjustment.getConfirmToken()).isEqualTo("confirmed");
        });
    }
}
