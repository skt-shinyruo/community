package com.nowcoder.community.growth.service;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.growth.entity.RewardOrder;
import com.nowcoder.community.growth.exception.GrowthErrorCode;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class RewardRedemptionServiceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RewardRedemptionService service;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from reward_order");
        jdbcTemplate.update("delete from reward_item");
        jdbcTemplate.update("delete from reward_ledger");
        jdbcTemplate.update("delete from reward_grant_record");
        jdbcTemplate.update("delete from reward_account");
        jdbcTemplate.update("delete from user_score_log");
        jdbcTemplate.update("update user set score = 0");
    }

    @Test
    void redeemShouldStoreImmutableItemSnapshotEvenWhenCatalogChangesLater() {
        seedRewardAccount(1, 30, 0);
        long itemId = insertItem("头像框周卡", "一周头像框权益", 12, 5, 1, "AUTO");

        RewardOrder order = service.redeem(1, itemId, "redeem-req-1");
        jdbcTemplate.update(
                "update reward_item set item_name = ?, item_desc = ?, cost_balance = ? where id = ?",
                "头像框月卡",
                "三十天头像框权益",
                30,
                itemId
        );

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select item_id, item_name_snapshot, item_desc_snapshot, cost_balance_snapshot, fulfillment_mode_snapshot, status from reward_order where id = ?",
                order.getId()
        );

        assertThat(((Number) row.get("ITEM_ID")).longValue()).isEqualTo(itemId);
        assertThat(row.get("ITEM_NAME_SNAPSHOT")).isEqualTo("头像框周卡");
        assertThat(row.get("ITEM_DESC_SNAPSHOT")).isEqualTo("一周头像框权益");
        assertThat(((Number) row.get("COST_BALANCE_SNAPSHOT")).intValue()).isEqualTo(12);
        assertThat(row.get("FULFILLMENT_MODE_SNAPSHOT")).isEqualTo("AUTO");
        assertThat(row.get("STATUS")).isEqualTo("FULFILLED");
        assertThat(availableBalanceOf(1)).isEqualTo(18);
        assertThat(frozenBalanceOf(1)).isZero();
        assertThat(scoreOf(1)).isZero();
    }

    @Test
    void sameRedeemRequestShouldNotCreateDuplicateOrders() {
        seedRewardAccount(1, 30, 0);
        long itemId = insertItem("社区称号", "限时称号", 8, 5, 1, "AUTO");

        RewardOrder first = service.redeem(1, itemId, "redeem-req-2");
        RewardOrder second = service.redeem(1, itemId, "redeem-req-2");

        assertThat(first.getId()).isEqualTo(second.getId());
        assertThat(countRows("reward_order")).isEqualTo(1);
        assertThat(availableBalanceOf(1)).isEqualTo(22);
        assertThat(scoreOf(1)).isZero();
    }

    @Test
    void sameRedeemRequestIdShouldBeScopedPerUser() {
        seedRewardAccount(1, 30, 0);
        seedRewardAccount(2, 30, 0);
        long itemId = insertItem("社区称号", "限时称号", 8, 5, 1, "AUTO");

        RewardOrder first = service.redeem(1, itemId, "shared-req-1");
        RewardOrder second = service.redeem(2, itemId, "shared-req-1");

        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(first.getUserId()).isEqualTo(1);
        assertThat(second.getUserId()).isEqualTo(2);
        assertThat(countRows("reward_order")).isEqualTo(2);
        assertThat(availableBalanceOf(1)).isEqualTo(22);
        assertThat(availableBalanceOf(2)).isEqualTo(22);
    }

    @Test
    void soldOutItemShouldRejectBeforeAnyBalanceMovement() {
        seedRewardAccount(1, 30, 0);
        long itemId = insertItem("数字贴纸", "库存为零", 5, 0, 1, "AUTO");

        assertThatThrownBy(() -> service.redeem(1, itemId, "redeem-req-3"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(GrowthErrorCode.REWARD_ITEM_SOLD_OUT))
                .hasMessageContaining("sold out");

        assertThat(countRows("reward_order")).isZero();
        assertThat(countRows("reward_ledger")).isZero();
        assertThat(availableBalanceOf(1)).isEqualTo(30);
        assertThat(scoreOf(1)).isZero();
    }

    @Test
    void perUserLimitShouldRejectSecondRedemptionBeforeBalanceMovement() {
        seedRewardAccount(1, 30, 0);
        long itemId = insertItem("稀有挂件", "每人限兑一次", 10, 5, 1, "AUTO");

        service.redeem(1, itemId, "redeem-req-4");

        assertThatThrownBy(() -> service.redeem(1, itemId, "redeem-req-5"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(GrowthErrorCode.REWARD_ITEM_LIMIT_EXCEEDED))
                .hasMessageContaining("limit");

        assertThat(countRows("reward_order")).isEqualTo(1);
        assertThat(availableBalanceOf(1)).isEqualTo(20);
    }

    @Test
    void manualItemShouldFreezeBalanceAndRemainPending() {
        seedRewardAccount(1, 30, 0);
        long itemId = insertItem("社群资格", "人工发放社群资格", 15, 5, 1, "MANUAL");

        RewardOrder order = service.redeem(1, itemId, "redeem-req-6");

        assertThat(order.getStatus()).isEqualTo("PENDING");
        assertThat(availableBalanceOf(1)).isEqualTo(15);
        assertThat(frozenBalanceOf(1)).isEqualTo(15);
        assertThat(scoreOf(1)).isZero();
    }

    @Test
    void cancellingPendingManualOrderShouldReleaseFrozenBalance() {
        seedRewardAccount(1, 30, 0);
        long itemId = insertItem("人工勋章", "管理员审核后发放", 9, 5, 1, "MANUAL");

        RewardOrder order = service.redeem(1, itemId, "redeem-req-7");
        RewardOrder cancelled = service.cancelPendingOrder(order.getId());

        assertThat(cancelled.getStatus()).isEqualTo("CANCELLED");
        assertThat(orderStatus(order.getId())).isEqualTo("CANCELLED");
        assertThat(availableBalanceOf(1)).isEqualTo(30);
        assertThat(frozenBalanceOf(1)).isZero();
        assertThat(scoreOf(1)).isZero();
    }

    @Test
    void refundingFulfilledOrderShouldRestoreAvailableBalanceExactlyOnce() {
        seedRewardAccount(1, 30, 0);
        long itemId = insertItem("限定头像框", "自动发放头像框", 11, 5, 1, "AUTO");

        RewardOrder order = service.redeem(1, itemId, "redeem-req-8");
        RewardOrder refunded = service.refundFulfilledOrder(order.getId());
        RewardOrder duplicated = service.refundFulfilledOrder(order.getId());

        assertThat(refunded.getStatus()).isEqualTo("REFUNDED");
        assertThat(duplicated.getStatus()).isEqualTo("REFUNDED");
        assertThat(orderStatus(order.getId())).isEqualTo("REFUNDED");
        assertThat(availableBalanceOf(1)).isEqualTo(30);
        assertThat(frozenBalanceOf(1)).isZero();
        assertThat(countRows("reward_ledger")).isEqualTo(2);
        assertThat(scoreOf(1)).isZero();
    }

    private void seedRewardAccount(int userId, int availableBalance, int frozenBalance) {
        jdbcTemplate.update(
                "insert into reward_account(user_id, available_balance, frozen_balance, version, update_time) values (?, ?, ?, 0, current_timestamp)",
                userId, availableBalance, frozenBalance
        );
    }

    private long insertItem(String itemName, String itemDesc, int costBalance, int stock, int perUserLimit, String fulfillmentMode) {
        jdbcTemplate.update(
                "insert into reward_item(item_name, item_desc, cost_balance, stock, per_user_limit, fulfillment_mode, status, create_time, update_time) values (?, ?, ?, ?, ?, ?, 'ACTIVE', current_timestamp, current_timestamp)",
                itemName, itemDesc, costBalance, stock, perUserLimit, fulfillmentMode
        );
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("select id from reward_item order by id desc limit 1");
        return ((Number) rows.get(0).get("ID")).longValue();
    }

    private int availableBalanceOf(int userId) {
        Integer balance = jdbcTemplate.queryForObject(
                "select available_balance from reward_account where user_id = ?",
                Integer.class,
                userId
        );
        return balance == null ? 0 : balance;
    }

    private int frozenBalanceOf(int userId) {
        Integer balance = jdbcTemplate.queryForObject(
                "select frozen_balance from reward_account where user_id = ?",
                Integer.class,
                userId
        );
        return balance == null ? 0 : balance;
    }

    private int scoreOf(int userId) {
        Integer score = jdbcTemplate.queryForObject(
                "select score from user where id = ?",
                Integer.class,
                userId
        );
        return score == null ? 0 : score;
    }

    private String orderStatus(long orderId) {
        return jdbcTemplate.queryForObject(
                "select status from reward_order where id = ?",
                String.class,
                orderId
        );
    }

    private int countRows(String tableName) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from " + tableName, Integer.class);
        return count == null ? 0 : count;
    }
}
