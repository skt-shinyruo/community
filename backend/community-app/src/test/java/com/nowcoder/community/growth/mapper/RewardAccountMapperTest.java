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
class RewardAccountMapperTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RewardAccountMapper rewardAccountMapper;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from reward_account");
    }

    @Test
    void addAvailableBalanceShouldRejectOverdraftAtomically() {
        jdbcTemplate.update(
                "insert into reward_account(user_id, available_balance, frozen_balance, version, update_time) values (1, 5, 0, 0, current_timestamp)"
        );

        int updated = rewardAccountMapper.addAvailableBalance(1, -6);

        assertThat(updated).isZero();
        assertThat(jdbcTemplate.queryForObject("select available_balance from reward_account where user_id = 1", Integer.class))
                .isEqualTo(5);
    }
}
