package com.nowcoder.community.growth.mapper;

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

import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
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
        UUID userId = uuid(1);
        jdbcTemplate.update(
                "insert into reward_account(user_id, available_balance, frozen_balance, version, update_time) values (?, 5, 0, 0, current_timestamp)",
                BinaryUuidCodec.toBytes(userId)
        );

        int updated = rewardAccountMapper.addAvailableBalance(userId, -6);

        assertThat(updated).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select available_balance from reward_account where user_id = ?",
                Integer.class,
                BinaryUuidCodec.toBytes(userId)
        ))
                .isEqualTo(5);
    }
}
