package com.nowcoder.community.user.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.wallet.application.WalletAccountApplicationService;
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
class UserPointsApplicationServiceIntegrationTest {

    private static final UUID USER_ID = uuid(1);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserPointsApplicationService userPointsApplicationService;

    @Autowired
    private WalletAccountApplicationService walletAccountService;

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
        jdbcTemplate.update(
                "merge into user (id, username, password, salt, email, type, status, header_url, create_time, score) key(id) " +
                        "values (?, ?, ?, ?, ?, ?, ?, ?, current_timestamp, 0)",
                BinaryUuidCodec.toBytes(USER_ID),
                "u1",
                "p",
                "s",
                "u1@example.com",
                0,
                1,
                "http://old.local/a.png"
        );
        jdbcTemplate.update("update user set score = 0");
    }

    @Test
    void postPublishedShouldCreditWalletWithoutMutatingLegacyScore() {
        int before = currentScore(USER_ID);

        userPointsApplicationService.project(new UserPointsApplicationService.PointsProjectionCommand(
                USER_ID,
                10,
                "post-evt-cutover-1",
                "PostPublished"
        ));

        assertThat(currentScore(USER_ID)).isEqualTo(before);
        assertThat(walletAccountService.balanceOfUser(USER_ID)).isEqualTo(10);
        assertThat(countRows("user_score_log")).isZero();
        assertThat(countRows("wallet_txn")).isEqualTo(1);
        assertThat(countRows("wallet_entry")).isEqualTo(2);
    }

    private int currentScore(UUID userId) {
        Integer score = jdbcTemplate.queryForObject(
                "select score from user where id = ?",
                Integer.class,
                BinaryUuidCodec.toBytes(userId)
        );
        return score == null ? 0 : score;
    }

    private int countRows(String tableName) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from " + tableName, Integer.class);
        return count == null ? 0 : count;
    }

}
