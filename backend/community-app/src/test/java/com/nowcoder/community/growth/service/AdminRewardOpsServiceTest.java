package com.nowcoder.community.growth.service;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.growth.dto.AdminGrowthMetricsResponse;
import com.nowcoder.community.growth.dto.AdminRewardItemResponse;
import com.nowcoder.community.growth.dto.AdminRewardItemUpsertRequest;
import com.nowcoder.community.growth.dto.AdminRewardOrderResponse;
import com.nowcoder.community.growth.dto.AdminRewardOrderActionRequest;
import com.nowcoder.community.growth.entity.RewardItem;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class AdminRewardOpsServiceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AdminRewardOpsService service;

    @Autowired
    private RewardRedemptionService rewardRedemptionService;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from admin_reward_order_action");
        jdbcTemplate.update("delete from reward_order");
        jdbcTemplate.update("delete from reward_item");
        jdbcTemplate.update("delete from reward_ledger");
        jdbcTemplate.update("delete from reward_account");
        jdbcTemplate.update("delete from admin_reward_adjustment");
        jdbcTemplate.update("delete from user_score_log");
        jdbcTemplate.update("update user set score = 0");
    }

    @Test
    void itemLifecycleShouldCreateEditAndDeactivateRewardItems() {
        AdminRewardItemUpsertRequest create = new AdminRewardItemUpsertRequest();
        create.setItemName("社群资格");
        create.setItemDesc("人工发放");
        create.setCostBalance(15);
        create.setStock(5);
        create.setPerUserLimit(1);
        create.setFulfillmentMode("MANUAL");
        create.setStatus("ACTIVE");

        RewardItem created = service.upsertItem(create);

        AdminRewardItemUpsertRequest update = new AdminRewardItemUpsertRequest();
        update.setItemId(created.getId());
        update.setItemName("社群资格月卡");
        update.setItemDesc("人工发放升级版");
        update.setCostBalance(20);
        update.setStock(3);
        update.setPerUserLimit(1);
        update.setFulfillmentMode("MANUAL");
        update.setStatus("INACTIVE");

        RewardItem updated = service.upsertItem(update);

        assertThat(created.getId()).isPositive();
        assertThat(updated.getItemName()).isEqualTo("社群资格月卡");
        assertThat(updated.getStatus()).isEqualTo("INACTIVE");
        assertThat(jdbcTemplate.queryForObject("select count(*) from reward_item", Integer.class)).isEqualTo(1);
    }

    @Test
    void upsertItemShouldRejectWhenUpdatingMissingItem() {
        AdminRewardItemUpsertRequest request = new AdminRewardItemUpsertRequest();
        request.setItemId(999L);
        request.setItemName("不存在奖品");
        request.setItemDesc("不存在");
        request.setCostBalance(10);
        request.setStock(1);
        request.setPerUserLimit(1);
        request.setFulfillmentMode("AUTO");
        request.setStatus("ACTIVE");

        assertThatThrownBy(() -> service.upsertItem(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(GrowthErrorCode.REWARD_ITEM_UNAVAILABLE))
                .hasMessageContaining("itemId=999");
    }

    @Test
    void upsertItemShouldRejectNegativeCostBalance() {
        AdminRewardItemUpsertRequest request = new AdminRewardItemUpsertRequest();
        request.setItemName("异常奖品");
        request.setItemDesc("非法配置");
        request.setCostBalance(-1);
        request.setStock(5);
        request.setPerUserLimit(1);
        request.setFulfillmentMode("AUTO");
        request.setStatus("ACTIVE");

        assertThatThrownBy(() -> service.upsertItem(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(GrowthErrorCode.INVALID_REQUEST))
                .hasMessageContaining("costBalance");
    }

    @Test
    void upsertItemShouldRejectUnsupportedFulfillmentMode() {
        AdminRewardItemUpsertRequest request = new AdminRewardItemUpsertRequest();
        request.setItemName("异常奖品");
        request.setItemDesc("非法配置");
        request.setCostBalance(10);
        request.setStock(5);
        request.setPerUserLimit(1);
        request.setFulfillmentMode("COUPON");
        request.setStatus("ACTIVE");

        assertThatThrownBy(() -> service.upsertItem(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(GrowthErrorCode.INVALID_REQUEST))
                .hasMessageContaining("fulfillmentMode");
    }

    @Test
    void upsertItemShouldRejectNegativeStock() {
        AdminRewardItemUpsertRequest request = new AdminRewardItemUpsertRequest();
        request.setItemName("异常奖品");
        request.setItemDesc("非法配置");
        request.setCostBalance(10);
        request.setStock(-1);
        request.setPerUserLimit(1);
        request.setFulfillmentMode("AUTO");
        request.setStatus("ACTIVE");

        assertThatThrownBy(() -> service.upsertItem(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(GrowthErrorCode.INVALID_REQUEST))
                .hasMessageContaining("stock");
    }

    @Test
    void upsertItemShouldRejectUnsupportedStatus() {
        AdminRewardItemUpsertRequest request = new AdminRewardItemUpsertRequest();
        request.setItemName("异常奖品");
        request.setItemDesc("非法配置");
        request.setCostBalance(10);
        request.setStock(1);
        request.setPerUserLimit(1);
        request.setFulfillmentMode("AUTO");
        request.setStatus("PUBLISHED");

        assertThatThrownBy(() -> service.upsertItem(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(GrowthErrorCode.INVALID_REQUEST))
                .hasMessageContaining("status");
    }

    @Test
    void processingManualOrderShouldSupportFulfillAndCancel() {
        seedRewardAccount(1, 30, 0);
        long itemId = insertItem("人工勋章", "需要运营发放", 12, 5, 1, "MANUAL", "ACTIVE");
        RewardOrder pendingOrder = rewardRedemptionService.redeem(1, itemId, "manual-admin-1");

        AdminRewardOrderActionRequest fulfill = new AdminRewardOrderActionRequest();
        fulfill.setOrderId(pendingOrder.getId());
        fulfill.setAction("FULFILL");
        fulfill.setConfirm(true);
        fulfill.setNote("issued");

        RewardOrder fulfilled = service.processOrder(99, fulfill);

        assertThat(fulfilled.getStatus()).isEqualTo("FULFILLED");
        assertThat(availableBalanceOf(1)).isEqualTo(18);
        assertThat(frozenBalanceOf(1)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from admin_reward_order_action", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select actor_user_id from admin_reward_order_action order by id asc limit 1", Integer.class)).isEqualTo(99);
        assertThat(jdbcTemplate.queryForObject("select action from admin_reward_order_action order by id asc limit 1", String.class)).isEqualTo("FULFILL");
        assertThat(jdbcTemplate.queryForObject("select note from admin_reward_order_action order by id asc limit 1", String.class)).isEqualTo("issued");
        assertThat(jdbcTemplate.queryForObject("select count(*) from reward_ledger where user_id = 1", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("select event_type from reward_ledger where user_id = 1 order by id desc limit 1", String.class)).isEqualTo("RewardOrderFulfilled");
        assertThat(jdbcTemplate.queryForObject("select frozen_balance_after from reward_ledger where user_id = 1 order by id desc limit 1", Integer.class)).isZero();

        seedRewardAccount(2, 30, 0);
        long itemId2 = insertItem("人工社群", "待审核", 10, 5, 1, "MANUAL", "ACTIVE");
        RewardOrder secondPending = rewardRedemptionService.redeem(2, itemId2, "manual-admin-2");

        AdminRewardOrderActionRequest cancel = new AdminRewardOrderActionRequest();
        cancel.setOrderId(secondPending.getId());
        cancel.setAction("CANCEL");
        cancel.setConfirm(true);
        cancel.setNote("rejected");

        RewardOrder cancelled = service.processOrder(99, cancel);

        assertThat(cancelled.getStatus()).isEqualTo("CANCELLED");
        assertThat(availableBalanceOf(2)).isEqualTo(30);
        assertThat(frozenBalanceOf(2)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from admin_reward_order_action", Integer.class)).isEqualTo(2);
    }

    @Test
    void refundingFulfilledOrderShouldBeIdempotentAndMetricsShouldSummarizeQueue() {
        seedRewardAccount(1, 30, 0);
        long autoItemId = insertItem("头像框周卡", "自动发放", 8, 5, 1, "AUTO", "ACTIVE");
        RewardOrder fulfilled = rewardRedemptionService.redeem(1, autoItemId, "auto-admin-1");

        AdminRewardOrderActionRequest refund = new AdminRewardOrderActionRequest();
        refund.setOrderId(fulfilled.getId());
        refund.setAction("REFUND");
        refund.setConfirm(true);
        refund.setNote("duplicate issue");

        RewardOrder refunded = service.processOrder(99, refund);
        RewardOrder duplicated = service.processOrder(99, refund);

        assertThat(refunded.getStatus()).isEqualTo("REFUNDED");
        assertThat(duplicated.getStatus()).isEqualTo("REFUNDED");
        assertThat(availableBalanceOf(1)).isEqualTo(30);

        seedRewardAccount(2, 30, 0);
        long pendingItemId = insertItem("人工挂件", "待人工处理", 11, 5, 1, "MANUAL", "ACTIVE");
        rewardRedemptionService.redeem(2, pendingItemId, "manual-admin-3");

        AdminGrowthMetricsResponse metrics = service.metrics();

        assertThat(metrics.getActiveItemCount()).isEqualTo(2);
        assertThat(metrics.getPendingOrderCount()).isEqualTo(1);
        assertThat(metrics.getRefundedOrderCount()).isEqualTo(1);
    }

    @Test
    void listItemResponsesShouldProjectRewardItems() {
        insertItem("社群资格", "人工发放", 15, 5, 1, "MANUAL", "ACTIVE");

        AdminRewardItemResponse response = service.listItemResponses().get(0);

        assertThat(response.getItemName()).isEqualTo("社群资格");
        assertThat(response.getItemDesc()).isEqualTo("人工发放");
        assertThat(response.getCostBalance()).isEqualTo(15);
        assertThat(response.getStock()).isEqualTo(5);
        assertThat(response.getPerUserLimit()).isEqualTo(1);
        assertThat(response.getFulfillmentMode()).isEqualTo("MANUAL");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void processOrderResponseShouldProjectOrderFields() {
        seedRewardAccount(1, 30, 0);
        long itemId = insertItem("人工勋章", "需要运营发放", 12, 5, 1, "MANUAL", "ACTIVE");
        RewardOrder pendingOrder = rewardRedemptionService.redeem(1, itemId, "manual-admin-response");

        AdminRewardOrderActionRequest fulfill = new AdminRewardOrderActionRequest();
        fulfill.setOrderId(pendingOrder.getId());
        fulfill.setAction("FULFILL");
        fulfill.setConfirm(true);
        fulfill.setNote("issued");

        AdminRewardOrderResponse response = service.processOrderResponse(99, fulfill);

        assertThat(response.getId()).isEqualTo(pendingOrder.getId());
        assertThat(response.getItemId()).isEqualTo(itemId);
        assertThat(response.getStatus()).isEqualTo("FULFILLED");
        assertThat(response.getCostBalanceSnapshot()).isEqualTo(12);
        assertThat(response.getFulfillmentModeSnapshot()).isEqualTo("MANUAL");
        assertThat(response.getItemNameSnapshot()).isEqualTo("人工勋章");
    }

    private void seedRewardAccount(int userId, int availableBalance, int frozenBalance) {
        jdbcTemplate.update(
                "insert into reward_account(user_id, available_balance, frozen_balance, version, update_time) values (?, ?, ?, 0, current_timestamp)",
                userId, availableBalance, frozenBalance
        );
    }

    private long insertItem(String itemName, String itemDesc, int costBalance, int stock, int perUserLimit, String fulfillmentMode, String status) {
        jdbcTemplate.update(
                "insert into reward_item(item_name, item_desc, cost_balance, stock, per_user_limit, fulfillment_mode, status, create_time, update_time) values (?, ?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp)",
                itemName, itemDesc, costBalance, stock, perUserLimit, fulfillmentMode, status
        );
        return jdbcTemplate.queryForObject("select id from reward_item order by id desc limit 1", Long.class);
    }

    private int availableBalanceOf(int userId) {
        Integer balance = jdbcTemplate.queryForObject("select available_balance from reward_account where user_id = ?", Integer.class, userId);
        return balance == null ? 0 : balance;
    }

    private int frozenBalanceOf(int userId) {
        Integer balance = jdbcTemplate.queryForObject("select frozen_balance from reward_account where user_id = ?", Integer.class, userId);
        return balance == null ? 0 : balance;
    }
}
