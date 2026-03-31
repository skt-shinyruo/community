package com.nowcoder.community.growth.service;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.growth.dto.AdminAdjustBalanceRequest;
import com.nowcoder.community.growth.mapper.AdminRewardAdjustmentMapper;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class AdminGrowthServiceTransactionTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AdminGrowthService service;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @MockBean
    private AdminRewardAdjustmentMapper adminRewardAdjustmentMapper;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from reward_ledger");
        jdbcTemplate.update("delete from reward_account");
        jdbcTemplate.update("delete from user_score_log");
        jdbcTemplate.update("update user set score = 0");
    }

    @Test
    void rewardBalanceAdjustmentShouldRollbackWhenAuditInsertFails() {
        jdbcTemplate.update(
                "insert into reward_account(user_id, available_balance, frozen_balance, version, update_time) values (1, 10, 0, 0, current_timestamp)"
        );

        AdminAdjustBalanceRequest request = new AdminAdjustBalanceRequest();
        request.setTargetUserId(1);
        request.setAssetType("REWARD_BALANCE");
        request.setDelta(5);
        request.setConfirm(true);
        request.setReason("manual compensation");

        doThrow(new DataIntegrityViolationException("audit insert failed"))
                .when(adminRewardAdjustmentMapper)
                .insert(any());

        assertThatThrownBy(() -> service.adjust(99, request))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("audit insert failed");

        assertThat(jdbcTemplate.queryForObject("select available_balance from reward_account where user_id = 1", Integer.class))
                .isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject("select count(*) from reward_ledger", Integer.class))
                .isZero();
    }
}
