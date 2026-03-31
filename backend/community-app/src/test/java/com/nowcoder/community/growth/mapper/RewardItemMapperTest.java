package com.nowcoder.community.growth.mapper;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class RewardItemMapperTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RewardItemMapper rewardItemMapper;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from reward_order");
        jdbcTemplate.update("delete from reward_item");
    }

    @Test
    void reserveStockForRedemptionShouldRejectWhenPendingOrderWouldExceedPerUserLimit() {
        long itemId = insertItem("限量徽章", "每人限兑一次", 5, 3, 1);
        jdbcTemplate.update(
                """
                insert into reward_order(
                    redeem_request_id,
                    user_id,
                    item_id,
                    status,
                    cost_balance_snapshot,
                    fulfillment_mode_snapshot,
                    item_name_snapshot,
                    item_desc_snapshot
                ) values (?, ?, ?, 'FULFILLED', 5, 'AUTO', ?, ?)
                """,
                "existing-order-1",
                1,
                itemId,
                "限量徽章",
                "每人限兑一次"
        );
        jdbcTemplate.update(
                """
                insert into reward_order(
                    redeem_request_id,
                    user_id,
                    item_id,
                    status,
                    cost_balance_snapshot,
                    fulfillment_mode_snapshot,
                    item_name_snapshot,
                    item_desc_snapshot
                ) values (?, ?, ?, 'PENDING', 5, 'AUTO', ?, ?)
                """,
                "current-order-2",
                1,
                itemId,
                "限量徽章",
                "每人限兑一次"
        );

        int updated = rewardItemMapper.reserveStockForRedemption(itemId, 1);

        assertThat(updated).isZero();
        assertThat(jdbcTemplate.queryForObject("select stock from reward_item where id = ?", Integer.class, itemId)).isEqualTo(3);
    }

    private long insertItem(String itemName, String itemDesc, int costBalance, int stock, int perUserLimit) {
        jdbcTemplate.update(
                "insert into reward_item(item_name, item_desc, cost_balance, stock, per_user_limit, fulfillment_mode, status, create_time, update_time) values (?, ?, ?, ?, ?, 'AUTO', 'ACTIVE', current_timestamp, current_timestamp)",
                itemName, itemDesc, costBalance, stock, perUserLimit
        );
        return jdbcTemplate.queryForObject("select id from reward_item order by id desc limit 1", Long.class);
    }
}
